import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { PortabilityPage } from '.';
import type { Schedule } from '@/features/portability/types';
import { renderWithProviders } from '@/testing/render';
import { server } from '@/testing/server';

const SCHEDULE_URL = '/api/beneficiaries/:beneficiaryId/schedule';

const schedule = (beneficiaryId: string): Schedule => ({
  beneficiaryId,
  installments: [
    {
      installmentId: 'P001',
      agreementId: 'CONV01',
      amount: 150,
      deductionDate: '2026-01-10',
      status: 'OPEN',
      eligible: true,
    },
  ],
});

const problem = (status: number, code: string) =>
  HttpResponse.json(
    { code, detail: code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

const findScheduleTable = () => screen.findByRole('table', { name: 'Agenda de descontos futuros' });

const consult = async (beneficiaryId: string) => {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Beneficiário'), beneficiaryId);
  await user.click(screen.getByRole('button', { name: 'Consultar agenda' }));
  return user;
};

describe('PortabilityPage — beneficiary and opt-in', () => {
  test('asks for opt-in when the schedule requires authorization, then loads it', async () => {
    let authorizedBody: unknown;
    server.use(
      http.get(SCHEDULE_URL, ({ params }) =>
        authorizedBody
          ? HttpResponse.json(schedule(String(params.beneficiaryId)))
          : problem(422, 'AUTHORIZATION_REQUIRED'),
      ),
      http.post('/api/authorizations', async ({ request }) => {
        authorizedBody = await request.json();
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    const authorize = await screen.findByRole('button', { name: 'Autorizar' });
    expect(authorize).toBeDisabled();
    await user.click(screen.getByLabelText(/Autorizo a consulta/));
    await user.click(authorize);

    expect(await findScheduleTable()).toBeInTheDocument();
    expect(authorizedBody).toEqual({ beneficiaryId: 'BEN001' });
    expect(screen.queryByRole('button', { name: 'Autorizar' })).not.toBeInTheDocument();
  });

  test('consults the beneficiary from the URL without a click and keeps it in the URL', async () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))));
    renderWithProviders(<PortabilityPage />);

    expect(await findScheduleTable()).toBeInTheDocument();
    expect(screen.getByLabelText('Beneficiário')).toHaveValue('BEN001');
  });

  test('writes the consulted beneficiary to the URL', async () => {
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN002'))));
    renderWithProviders(<PortabilityPage />);

    await consult('BEN002');

    expect(await findScheduleTable()).toBeInTheDocument();
    expect(window.location.search).toBe('?beneficiary=BEN002');
  });

  test('offers a retry when the Central is unavailable', async () => {
    let calls = 0;
    server.use(
      http.get(SCHEDULE_URL, () =>
        ++calls === 1
          ? problem(503, 'CENTRAL_CONVENIOS_UNAVAILABLE')
          : HttpResponse.json(schedule('BEN001')),
      ),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A Central de Convênios está indisponível no momento.',
    );
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    expect(await findScheduleTable()).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('requires a beneficiary before consulting', async () => {
    renderWithProviders(<PortabilityPage />);

    await consult('   ');

    expect(screen.getByText('Informe o beneficiário.')).toBeInTheDocument();
  });
});

describe('PortabilityPage — schedule and selection', () => {
  test('lists installments, blocks ineligible ones and totals the selection', async () => {
    server.use(
      http.get(SCHEDULE_URL, () =>
        HttpResponse.json({
          beneficiaryId: 'BEN001',
          installments: [
            ...schedule('BEN001').installments,
            {
              installmentId: 'P002',
              agreementId: 'CONV01',
              amount: 200.5,
              deductionDate: '2026-02-10',
              status: 'OPEN',
              eligible: false,
            },
          ],
        } satisfies Schedule),
      ),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    const table = await findScheduleTable();
    const p001 = within(table).getByRole('row', { name: /P001/ });
    const p002 = within(table).getByRole('row', { name: /P002/ });

    expect(p001).toHaveTextContent('CONV01');
    expect(p001).toHaveTextContent('R$ 150,00');
    expect(p001).toHaveTextContent('10/01/2026');
    expect(p001).toHaveTextContent('Aberta');
    expect(screen.getByLabelText('Selecionar parcela P002')).toBeDisabled();
    expect(p002).toHaveTextContent('Em portabilidade');
    expect(table).toHaveTextContent('0 parcela(s) selecionada(s) · Total R$ 0,00');

    await user.click(screen.getByLabelText('Selecionar parcela P001'));

    expect(table).toHaveTextContent('1 parcela(s) selecionada(s) · Total R$ 150,00');
  });

  test('says when the schedule has no installments', async () => {
    server.use(
      http.get(SCHEDULE_URL, () =>
        HttpResponse.json({ beneficiaryId: 'BEN_ERRO', installments: [] }),
      ),
    );
    renderWithProviders(<PortabilityPage />);

    await consult('BEN_ERRO');

    expect(
      await screen.findByText('Nenhuma parcela futura para este beneficiário.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  test('clears the selection when another beneficiary is consulted', async () => {
    server.use(
      http.get(SCHEDULE_URL, ({ params }) =>
        HttpResponse.json(schedule(String(params.beneficiaryId))),
      ),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    await findScheduleTable();
    await user.click(screen.getByLabelText('Selecionar parcela P001'));
    await user.clear(screen.getByLabelText('Beneficiário'));
    await consult('BEN002');

    expect(await screen.findByLabelText('Selecionar parcela P001')).not.toBeChecked();
  });
});
