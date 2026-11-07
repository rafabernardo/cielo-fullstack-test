
// mock-central.js — simulador da Central de Convênios (fornecido para o desafio)
// Rodar com: node mock-central.js

const http = require('http');
const { randomUUID } = require('crypto');

const AGENDAS = {
  BEN001: [
    { parcelaId: 'P001', convenioId: 'CONV01', valor: 150.00, dataDesconto: '2026-01-10', status: 'ABERTA' },
    { parcelaId: 'P002', convenioId: 'CONV01', valor: 150.00, dataDesconto: '2026-02-10', status: 'ABERTA' },
  ],
  BEN002: [
    { parcelaId: 'P010', convenioId: 'CONV02', valor: 300.00, dataDesconto: '2026-01-15', status: 'ABERTA' },
  ],
};

const operacoes = new Map();

function send(res, status, body, extraHeaders = {}) {
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', ...extraHeaders });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (chunk) => (data += chunk));
    req.on('end', () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch {
        resolve({});
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const parts = url.pathname.split('/').filter(Boolean);

  if (req.method === 'POST' && parts[0] === 'autorizacoes') {
    await readBody(req);
    return send(res, 201, { autorizacaoId: randomUUID() });
  }

  if (req.method === 'GET' && parts[0] === 'beneficiarios' && parts[2] === 'agenda') {
    return send(res, 200, AGENDAS[parts[1]] || []);
  }

  if (req.method === 'POST' && parts[0] === 'operacoes-portabilidade') {
    const body = await readBody(req);

    if (body.beneficiarioId === 'BEN_ERRO') {
      return send(res, 503, {
        erro: 'Central indisponível no momento (simulado). Tente novamente.',
      });
    }

    const protocolo = randomUUID();
    const vaiRejeitar = body.beneficiarioId === 'BEN002';

    operacoes.set(protocolo, {
      resolveEm: Date.now() + 5000,
      vaiRejeitar,
    });

    return send(res, 202, { protocolo });
  }

  if (req.method === 'GET' && parts[0] === 'operacoes-portabilidade' && parts[1]) {
    const op = operacoes.get(parts[1]);

    if (!op) {
      return send(res, 404, { erro: 'Protocolo não encontrado' });
    }

    if (Date.now() < op.resolveEm) {
      return send(res, 204, undefined, { 'Retry-After': '2' });
    }

    return send(res, 200, {
      status: op.vaiRejeitar ? 'REJEITADA' : 'CONFIRMADA',
      motivoRejeicao: op.vaiRejeitar
        ? 'Beneficiário atingiu o limite de margem consignável'
        : null,
    });
  }

  send(res, 404, { erro: 'Rota não encontrada' });
});

server.listen(4000, () =>
  console.log('Central de Convênios (simulada) em http://localhost:4000')
);

