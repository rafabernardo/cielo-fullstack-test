import { screen } from '@testing-library/react';
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

    expect(await screen.findByText('1 parcela(s) na agenda.')).toBeInTheDocument();
    expect(authorizedBody).toEqual({ beneficiaryId: 'BEN001' });
    expect(screen.queryByRole('button', { name: 'Autorizar' })).not.toBeInTheDocument();
  });

  test('consults the beneficiary from the URL without a click and keeps it in the URL', async () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))));
    renderWithProviders(<PortabilityPage />);

    expect(await screen.findByText('1 parcela(s) na agenda.')).toBeInTheDocument();
    expect(screen.getByLabelText('Beneficiário')).toHaveValue('BEN001');
  });

  test('writes the consulted beneficiary to the URL', async () => {
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN002'))));
    renderWithProviders(<PortabilityPage />);

    await consult('BEN002');

    expect(await screen.findByText('1 parcela(s) na agenda.')).toBeInTheDocument();
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

    expect(await screen.findByText('1 parcela(s) na agenda.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('requires a beneficiary before consulting', async () => {
    renderWithProviders(<PortabilityPage />);

    await consult('   ');

    expect(screen.getByText('Informe o beneficiário.')).toBeInTheDocument();
  });
});
