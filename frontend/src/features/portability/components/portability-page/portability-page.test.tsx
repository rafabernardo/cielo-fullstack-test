import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
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

describe('PortabilityPage — destination account and request', () => {
  const OPERATIONS_URL = '/api/portability-operations';

  const operation = (id: string) => ({
    id,
    beneficiaryId: 'BEN001',
    installmentIds: ['P001'],
    destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
    status: 'PENDING',
    rejectionReason: null,
    createdAt: '2026-11-08T12:00:00Z',
    updatedAt: '2026-11-08T12:00:00Z',
  });

  // Opens BEN001 with P001 selected.
  const selectInstallment = async () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))));
    renderWithProviders(<PortabilityPage />);
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText('Selecionar parcela P001'));
    return user;
  };

  const fillAccount = async (
    user: ReturnType<typeof userEvent.setup>,
    { bankCode = '341', branch = '0001', accountNumber = '123456-7' } = {},
  ) => {
    await user.type(screen.getByLabelText('Banco'), bankCode);
    await user.type(screen.getByLabelText('Agência'), branch);
    await user.type(screen.getByLabelText('Conta'), accountNumber);
  };

  const requestButton = () => screen.getByRole('button', { name: 'Solicitar portabilidade' });

  test('keeps the request disabled until an installment is selected', async () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))));
    renderWithProviders(<PortabilityPage />);
    await findScheduleTable();

    expect(requestButton()).toBeDisabled();
  });

  test('validates the account before sending anything', async () => {
    let posts = 0;
    server.use(http.post(OPERATIONS_URL, () => (posts++, HttpResponse.json({}, { status: 202 }))));
    const user = await selectInstallment();

    await fillAccount(user, { bankCode: '12', branch: 'abc', accountNumber: '12a' });
    await user.click(requestButton());

    expect(await screen.findByText('Banco deve ter 3 dígitos')).toBeInTheDocument();
    expect(screen.getByText('Agência deve ter 4 dígitos')).toBeInTheDocument();
    expect(screen.getByText(/Conta: até 12 dígitos/)).toBeInTheDocument();
    expect(screen.getByLabelText('Banco')).toHaveAccessibleDescription('Banco deve ter 3 dígitos');
    expect(posts).toBe(0);
  });

  test('shows field errors returned by the backend under their field', async () => {
    server.use(
      http.post(OPERATIONS_URL, () =>
        HttpResponse.json(
          {
            code: 'VALIDATION_ERROR',
            errors: { 'destinationAccount.branch': 'Agência inexistente' },
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const user = await selectInstallment();

    await fillAccount(user);
    await user.click(requestButton());

    expect(await screen.findByText('Agência inexistente')).toBeInTheDocument();
    expect(screen.getByLabelText('Agência')).toHaveAccessibleDescription('Agência inexistente');
  });

  test('offers a retry when the Central is unavailable and creates the operation on retry', async () => {
    const bodies: unknown[] = [];
    server.use(
      http.post(OPERATIONS_URL, async ({ request }) => {
        bodies.push(await request.json());
        return bodies.length === 1
          ? problem(503, 'CENTRAL_CONVENIOS_UNAVAILABLE')
          : HttpResponse.json(operation('op-1'), { status: 202 });
      }),
    );
    const user = await selectInstallment();

    await fillAccount(user);
    await user.click(requestButton());
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A Central de Convênios está indisponível no momento.',
    );
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Operação op-1 criada.');
    expect(bodies).toEqual([
      {
        beneficiaryId: 'BEN001',
        installmentIds: ['P001'],
        destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
      },
      bodies[0],
    ]);
  });

  test('sends a single request when the button is clicked twice', async () => {
    let posts = 0;
    server.use(
      http.post(OPERATIONS_URL, async () => {
        posts++;
        await delay(50);
        return HttpResponse.json(operation('op-1'), { status: 202 });
      }),
    );
    const user = await selectInstallment();

    await fillAccount(user);
    await user.dblClick(requestButton());

    expect(await screen.findByRole('status')).toHaveTextContent('Operação op-1 criada.');
    expect(posts).toBe(1);
  });
});
