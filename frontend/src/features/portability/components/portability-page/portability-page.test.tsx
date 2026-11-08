import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { delay, http, HttpResponse, sse } from 'msw';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { PortabilityPage } from '.';
import type { PortabilityOperation, Schedule } from '@/features/portability/types';
import { renderWithProviders } from '@/testing/render';
import { server } from '@/testing/server';

const SCHEDULE_URL = '/api/beneficiaries/:beneficiaryId/schedule';
const HISTORY_URL = '/api/beneficiaries/:beneficiaryId/portability-operations';
const AUTHORIZATION_URL = '/api/beneficiaries/:beneficiaryId/authorization';

const historyPage = (content: PortabilityOperation[], totalPages = content.length ? 1 : 0) =>
  HttpResponse.json({
    content,
    page: { size: 5, number: 0, totalElements: content.length, totalPages },
  });

// Every test starts authorized and with an empty history; tests about either override it.
beforeEach(() =>
  server.use(
    http.get(AUTHORIZATION_URL, ({ params }) =>
      HttpResponse.json({ beneficiaryId: params.beneficiaryId, authorized: true }),
    ),
    http.get(HISTORY_URL, () => historyPage([])),
  ),
);

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
      heldBy: null,
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

const OPERATIONS_URL = '/api/portability-operations';

// Answers POST /portability-operations with `respond`; every operation it creates (202) is listed in
// the history, newest first, as the backend does.
const operationsBackend = (
  respond: (info: { request: Request }) => Response | Promise<Response>,
) => {
  const created: PortabilityOperation[] = [];
  let historyReads = 0;
  server.use(
    http.post(OPERATIONS_URL, async (info) => {
      const response = await respond(info);
      if (response.status === 202) created.unshift(await response.clone().json());
      return response;
    }),
    http.get(HISTORY_URL, () => (historyReads++, historyPage(created))),
  );
  return { historyReads: () => historyReads };
};

const history = () => screen.getByRole('region', { name: 'Histórico de portabilidade' });
const findHistoryRow = (name: RegExp) =>
  waitFor(() => within(history()).getByRole('row', { name }));

describe('PortabilityPage — beneficiary and opt-in', () => {
  test('asks for opt-in before reading the schedule, then loads it', async () => {
    let authorizedBody: unknown;
    let scheduleReads = 0;
    server.use(
      http.get(AUTHORIZATION_URL, ({ params }) =>
        HttpResponse.json({ beneficiaryId: params.beneficiaryId, authorized: !!authorizedBody }),
      ),
      http.get(
        SCHEDULE_URL,
        ({ params }) => (
          scheduleReads++,
          HttpResponse.json(schedule(String(params.beneficiaryId)))
        ),
      ),
      http.post('/api/authorizations', async ({ request }) => {
        authorizedBody = await request.json();
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    const authorize = await screen.findByRole('button', { name: 'Autorizar' });
    expect(scheduleReads).toBe(0);
    expect(authorize).toBeDisabled();
    await user.click(screen.getByLabelText(/Autorizo a consulta/));
    await user.click(authorize);

    expect(await findScheduleTable()).toBeInTheDocument();
    expect(scheduleReads).toBe(1);
    expect(authorizedBody).toEqual({ beneficiaryId: 'BEN001' });
    expect(screen.queryByRole('button', { name: 'Autorizar' })).not.toBeInTheDocument();
  });

  test('reads the history only once the beneficiary is authorized', async () => {
    let authorized = false;
    let historyReads = 0;
    server.use(
      http.get(AUTHORIZATION_URL, ({ params }) =>
        HttpResponse.json({ beneficiaryId: params.beneficiaryId, authorized }),
      ),
      http.get(SCHEDULE_URL, ({ params }) =>
        HttpResponse.json(schedule(String(params.beneficiaryId))),
      ),
      http.get(HISTORY_URL, () => (historyReads++, historyPage([]))),
      http.post('/api/authorizations', () => {
        authorized = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    await screen.findByRole('button', { name: 'Autorizar' });
    expect(historyReads).toBe(0);

    await user.click(screen.getByLabelText(/Autorizo a consulta/));
    await user.click(screen.getByRole('button', { name: 'Autorizar' }));
    await findScheduleTable();
    expect(historyReads).toBe(1);
  });

  test('fetches authorization and schedule once each under StrictMode', async () => {
    server.use(
      http.get(SCHEDULE_URL, ({ params }) =>
        HttpResponse.json(schedule(String(params.beneficiaryId))),
      ),
    );
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderWithProviders(
      <StrictMode>
        <PortabilityPage />
      </StrictMode>,
    );

    await consult('BEN001');
    await findScheduleTable();

    const paths = fetchSpy.mock.calls.map(([url]) => new URL(String(url)).pathname);
    expect(paths.filter((path) => path.endsWith('/authorization'))).toHaveLength(1);
    expect(paths.filter((path) => path.endsWith('/schedule'))).toHaveLength(1);
    fetchSpy.mockRestore();
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

    expect(screen.queryByText(/BEN001 → confirma/)).not.toBeInTheDocument();
    await consult('   ');

    expect(screen.getByText('Informe o beneficiário.')).toBeInTheDocument();
    expect(screen.getByLabelText('Beneficiário')).toHaveAccessibleDescription(
      /Informe o beneficiário\. BEN001 → confirma/,
    );
  });
});

describe('PortabilityPage — schedule and selection', () => {
  test('lists installments with their situation, blocks held ones and totals the selection', async () => {
    const held = (installmentId: string, status: 'PENDING' | 'CONFIRMED') => ({
      ...schedule('BEN001').installments[0],
      installmentId,
      eligible: false,
      heldBy: { operationId: `op-${installmentId}`, status },
    });
    server.use(
      http.get(SCHEDULE_URL, () =>
        HttpResponse.json({
          beneficiaryId: 'BEN001',
          installments: [
            ...schedule('BEN001').installments,
            held('P002', 'PENDING'),
            held('P003', 'CONFIRMED'),
          ],
        } satisfies Schedule),
      ),
    );
    renderWithProviders(<PortabilityPage />);

    const user = await consult('BEN001');
    const table = await findScheduleTable();
    const p001 = within(table).getByRole('row', { name: /P001/ });
    const p002 = within(table).getByRole('row', { name: /P002/ });
    const p003 = within(table).getByRole('row', { name: /P003/ });

    expect(p001).toHaveTextContent('CONV01');
    expect(p001).toHaveTextContent('R$ 150,00');
    expect(p001).toHaveTextContent('10/01/2026');
    expect(p001).toHaveTextContent('Disponível');
    expect(screen.getByLabelText('Selecionar parcela P002')).toBeDisabled();
    expect(p002).toHaveTextContent('Em portabilidade');
    expect(screen.getByLabelText('Selecionar parcela P003')).toBeDisabled();
    expect(p003).toHaveTextContent('Portada');
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
  const EVENTS_URL = '/api/portability-operations/:id/events';

  const operation = (id: string): PortabilityOperation => ({
    id,
    beneficiaryId: 'BEN001',
    installmentIds: ['P001'],
    destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
    status: 'PENDING',
    rejectionReason: null,
    createdAt: '2026-11-08T12:00:00Z',
    updatedAt: '2026-11-08T12:00:00Z',
  });

  // Opens BEN001 with P001 selected; created operations stay PENDING.
  const selectInstallment = async () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(
      http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))),
      sse<{ status: string }>(EVENTS_URL, ({ client, params }) => {
        client.send({ event: 'status', data: JSON.stringify(operation(String(params.id))) });
      }),
    );
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

  test('asks for the destination account only once an installment is selected', async () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))));
    renderWithProviders(<PortabilityPage />);
    const user = userEvent.setup();
    await findScheduleTable();

    expect(screen.queryByRole('group', { name: 'Conta de destino' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Solicitar portabilidade' }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByLabelText('Selecionar parcela P001'));

    expect(screen.getByRole('group', { name: 'Conta de destino' })).toBeInTheDocument();
    expect(requestButton()).toBeEnabled();
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
    operationsBackend(async ({ request }) => {
      bodies.push(await request.json());
      return bodies.length === 1
        ? problem(503, 'CENTRAL_CONVENIOS_UNAVAILABLE')
        : HttpResponse.json(operation('op-1'), { status: 202 });
    });
    const user = await selectInstallment();

    await fillAccount(user);
    await user.click(requestButton());
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A Central de Convênios está indisponível no momento.',
    );
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    expect(await findHistoryRow(/P001.*Pendente/)).toBeInTheDocument();
    expect(bodies).toEqual([
      {
        beneficiaryId: 'BEN001',
        installmentIds: ['P001'],
        destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
      },
      bodies[0],
    ]);
  });

  // BEN_ERRO in the simulator: no agenda, and the Central refuses the submission.
  test('lets an empty schedule be submitted so the Central unavailability is shown', async () => {
    let body: unknown;
    window.history.replaceState(null, '', '/?beneficiary=BEN_ERRO');
    server.use(
      http.get(SCHEDULE_URL, () =>
        HttpResponse.json({ beneficiaryId: 'BEN_ERRO', installments: [] }),
      ),
      http.post(OPERATIONS_URL, async ({ request }) => {
        body = await request.json();
        return problem(503, 'CENTRAL_CONVENIOS_UNAVAILABLE');
      }),
    );
    renderWithProviders(<PortabilityPage />);
    const user = userEvent.setup();
    await screen.findByText('Nenhuma parcela futura para este beneficiário.');

    await fillAccount(user);
    await user.click(requestButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A Central de Convênios está indisponível no momento.',
    );
    expect(body).toMatchObject({ beneficiaryId: 'BEN_ERRO', installmentIds: [] });
  });

  test('sends a single request when the button is clicked twice', async () => {
    let posts = 0;
    operationsBackend(async () => {
      posts++;
      await delay(50);
      return HttpResponse.json(operation('op-1'), { status: 202 });
    });
    const user = await selectInstallment();

    await fillAccount(user);
    await user.dblClick(requestButton());

    expect(await findHistoryRow(/P001/)).toBeInTheDocument();
    expect(posts).toBe(1);
  });
});

describe('PortabilityPage — following operations in the history', () => {
  const EVENTS_URL = '/api/portability-operations/:id/events';

  const pending = (id: string, installmentIds = ['P001']): PortabilityOperation => ({
    id,
    beneficiaryId: 'BEN001',
    installmentIds,
    destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
    status: 'PENDING',
    rejectionReason: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  });

  // Streams PENDING right away, then `outcome` (if any) shortly after, like the backend does.
  const streamStatus = (outcome?: Partial<PortabilityOperation>) =>
    sse<{ status: string }>(EVENTS_URL, ({ client, params }) => {
      const operation = pending(String(params.id));
      client.send({ event: 'status', data: JSON.stringify(operation) });
      if (outcome) {
        setTimeout(
          () =>
            client.send({ event: 'status', data: JSON.stringify({ ...operation, ...outcome }) }),
          50,
        );
      }
    });

  const requestPortability = async (postResponse: () => Response) => {
    let scheduleReads = 0;
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(
      http.get(SCHEDULE_URL, () => {
        scheduleReads++;
        return HttpResponse.json(schedule('BEN001'));
      }),
    );
    const backend = operationsBackend(postResponse);
    renderWithProviders(<PortabilityPage />);
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText('Selecionar parcela P001'));
    await user.type(screen.getByLabelText('Banco'), '341');
    await user.type(screen.getByLabelText('Agência'), '0001');
    await user.type(screen.getByLabelText('Conta'), '123456-7');
    await user.click(screen.getByRole('button', { name: 'Solicitar portabilidade' }));
    return { user, scheduleReads: () => scheduleReads, ...backend };
  };

  test('shows the new request waiting for the Central, then its confirmation', async () => {
    server.use(streamStatus({ status: 'CONFIRMED' }));

    const { scheduleReads } = await requestPortability(() =>
      HttpResponse.json(pending('op-1'), { status: 202 }),
    );

    expect(await findHistoryRow(/P001.*Pendente.*Aguardando a Central · \d+s/)).toBeInTheDocument();
    // The requested installment leaves the selection; the schedule is read again.
    expect(screen.getByLabelText('Selecionar parcela P001')).not.toBeChecked();
    expect(await findHistoryRow(/P001.*Confirmada/)).toBeInTheDocument();
    // Read on load, after the request and after the outcome.
    await waitFor(() => expect(scheduleReads()).toBe(3));
    expect(history()).not.toHaveTextContent('op-1');
  });

  test('shows every pending request at once', async () => {
    const requested: string[][] = [];
    const installment = (installmentId: string) => ({
      ...schedule('BEN001').installments[0],
      installmentId,
      eligible: !requested.flat().includes(installmentId),
      heldBy: requested.flat().includes(installmentId)
        ? { operationId: 'op-held', status: 'PENDING' as const }
        : null,
    });
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(
      http.get(SCHEDULE_URL, () =>
        HttpResponse.json({
          beneficiaryId: 'BEN001',
          installments: [installment('P001'), installment('P002')],
        }),
      ),
      // Both stay PENDING: the rows keep what the history listed.
      sse(EVENTS_URL, () => {}),
    );
    operationsBackend(async ({ request }) => {
      const { installmentIds } = (await request.json()) as { installmentIds: string[] };
      requested.push(installmentIds);
      return HttpResponse.json(pending(`op-${requested.length}`, installmentIds), { status: 202 });
    });
    renderWithProviders(<PortabilityPage />);
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText('Selecionar parcela P001'));
    await user.type(screen.getByLabelText('Banco'), '341');
    await user.type(screen.getByLabelText('Agência'), '0001');
    await user.type(screen.getByLabelText('Conta'), '123456-7');
    await user.click(screen.getByRole('button', { name: 'Solicitar portabilidade' }));
    await findHistoryRow(/P001.*Pendente/);

    await waitFor(() => expect(screen.getByLabelText('Selecionar parcela P001')).toBeDisabled());
    await user.click(screen.getByLabelText('Selecionar parcela P002'));
    // The account typed for the first request is still there.
    expect(screen.getByLabelText('Banco')).toHaveValue('341');
    await user.click(screen.getByRole('button', { name: 'Solicitar portabilidade' }));

    expect(await findHistoryRow(/P002.*Pendente/)).toBeInTheDocument();
    expect(await findHistoryRow(/P001.*Pendente/)).toBeInTheDocument();
    expect(requested).toEqual([['P001'], ['P002']]);
  });

  test('shows the rejection reason in the row, not as an error', async () => {
    server.use(
      streamStatus({
        status: 'REJECTED',
        rejectionReason: 'Beneficiário atingiu o limite de margem consignável',
      }),
    );

    await requestPortability(() => HttpResponse.json(pending('op-2'), { status: 202 }));

    expect(
      await findHistoryRow(/Rejeitada.*Beneficiário atingiu o limite de margem consignável/),
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('points to the history when the installments are already pending', async () => {
    const { historyReads } = await requestPortability(() =>
      HttpResponse.json(
        {
          code: 'INSTALLMENT_IN_PENDING_OPERATION',
          installmentIds: ['P001'],
          operationIds: ['op-9'],
        },
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Já existe uma solicitação em andamento para essas parcelas — acompanhe no Histórico de portabilidade.',
    );
    await waitFor(() => expect(historyReads()).toBe(2));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('PortabilityPage — history', () => {
  const operationOf = (
    id: string,
    status: PortabilityOperation['status'],
    extra: Partial<PortabilityOperation> = {},
  ): PortabilityOperation => ({
    id,
    beneficiaryId: 'BEN001',
    installmentIds: ['P001'],
    destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
    status,
    rejectionReason: null,
    createdAt: '2026-11-08T12:00:00Z',
    updatedAt: '2026-11-08T12:00:00Z',
    ...extra,
  });

  const openBeneficiary = () => {
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(http.get(SCHEDULE_URL, () => HttpResponse.json(schedule('BEN001'))));
    renderWithProviders(<PortabilityPage />);
  };

  const historyRows = () => within(history()).getAllByRole('row').slice(1);

  test('keeps pending rows up to date after a reload and details the others', async () => {
    server.use(
      http.get(HISTORY_URL, () =>
        historyPage([
          operationOf('op-new', 'PENDING'),
          operationOf('op-old', 'REJECTED', { rejectionReason: 'Sem margem' }),
        ]),
      ),
      sse<{ status: string }>('/api/portability-operations/:id/events', ({ client, params }) => {
        const operation = operationOf(String(params.id), 'PENDING');
        client.send({ event: 'status', data: JSON.stringify(operation) });
        setTimeout(
          () =>
            client.send({
              event: 'status',
              data: JSON.stringify({ ...operation, status: 'CONFIRMED' }),
            }),
          50,
        );
      }),
    );
    const user = userEvent.setup();

    openBeneficiary();

    expect(await findHistoryRow(/Pendente/)).toBeInTheDocument();
    expect(await findHistoryRow(/Confirmada/)).toBeInTheDocument();
    const [confirmed, rejected] = historyRows();
    expect(confirmed).toHaveTextContent('Confirmada');
    expect(rejected).toHaveTextContent('Rejeitada');
    expect(rejected).toHaveTextContent('Sem margem');
    expect(
      within(rejected).queryByRole('button', { name: 'Tentar novamente' }),
    ).not.toBeInTheDocument();

    await user.click(within(rejected).getByRole('button', { name: /Detalhes da solicitação/ }));

    expect(history()).toHaveTextContent('Banco 341 · Agência 0001 · Conta 123456-7');
  });

  test('resends a failed operation from its row', async () => {
    let sent: unknown;
    const failed = operationOf('op-failed', 'FAILED');
    window.history.replaceState(null, '', '/?beneficiary=BEN001');
    server.use(
      http.get(SCHEDULE_URL, () =>
        HttpResponse.json(
          sent
            ? {
                beneficiaryId: 'BEN001',
                installments: [
                  {
                    ...schedule('BEN001').installments[0],
                    eligible: false,
                    heldBy: { operationId: 'op-retry', status: 'PENDING' },
                  },
                ],
              }
            : schedule('BEN001'),
        ),
      ),
      http.get(HISTORY_URL, () =>
        historyPage(sent ? [operationOf('op-retry', 'PENDING'), failed] : [failed]),
      ),
      http.post(OPERATIONS_URL, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(operationOf('op-retry', 'PENDING'), { status: 202 });
      }),
      sse<{ status: string }>('/api/portability-operations/:id/events', ({ client, params }) => {
        client.send({
          event: 'status',
          data: JSON.stringify(operationOf(String(params.id), 'PENDING')),
        });
      }),
    );
    renderWithProviders(<PortabilityPage />);
    const user = userEvent.setup();

    const row = await findHistoryRow(/Falhou.*Falha na comunicação com a Central/);
    await user.click(within(row).getByRole('button', { name: 'Tentar novamente' }));

    expect(await findHistoryRow(/Pendente/)).toBeInTheDocument();
    expect(sent).toEqual({
      beneficiaryId: 'BEN001',
      installmentIds: ['P001'],
      destinationAccount: { bankCode: '341', branch: '0001', accountNumber: '123456-7' },
    });
    // The retry holds the installments now, so the failed row can't be resent again.
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Tentar novamente' })).not.toBeInTheDocument(),
    );
  });

  test('pages through the history five at a time', async () => {
    const requestedPages: (string | null)[] = [];
    server.use(
      http.get(HISTORY_URL, ({ request }) => {
        const url = new URL(request.url);
        requestedPages.push(url.searchParams.get('page'));
        expect(url.searchParams.get('size')).toBe('5');
        const page = Number(url.searchParams.get('page'));
        return HttpResponse.json({
          content: [operationOf(`op-page-${page}`, 'REJECTED', { installmentIds: [`P00${page}`] })],
          page: { size: 5, number: page, totalElements: 6, totalPages: 2 },
        });
      }),
    );
    openBeneficiary();
    const user = userEvent.setup();

    expect(await screen.findByText('Página 1 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Próxima' }));

    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument();
    expect(historyRows()).toHaveLength(1);
    expect(historyRows()[0]).toHaveTextContent('P001');
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled();
    expect(requestedPages).toEqual(['0', '1']);
  });

  test('says when the beneficiary has no operations', async () => {
    openBeneficiary();

    expect(await screen.findByText('Nenhuma portabilidade solicitada.')).toBeInTheDocument();
  });
});
