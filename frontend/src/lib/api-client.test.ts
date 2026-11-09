import { delay, http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import { ApiError, apiGet, apiPost } from './api-client';
import { server } from '@/testing/server';

test('returns the JSON body of a successful response', async () => {
  server.use(http.get('/api/things', () => HttpResponse.json({ id: 1 })));

  await expect(apiGet('/things')).resolves.toEqual({ id: 1 });
});

test('sends the body as JSON', async () => {
  server.use(
    http.post('/api/things', async ({ request }) =>
      HttpResponse.json(await request.json(), { status: 201 }),
    ),
  );

  await expect(apiPost('/things', { name: 'x' })).resolves.toEqual({ name: 'x' });
});

test('throws an ApiError carrying the problem of an error response', async () => {
  server.use(
    http.get('/api/things', () =>
      HttpResponse.json(
        { code: 'INSTALLMENT_NOT_ELIGIBLE', detail: 'Not eligible', installmentIds: ['P1'] },
        { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    ),
  );

  const error = await apiGet('/things').catch((e: unknown) => e);

  expect(error).toBeInstanceOf(ApiError);
  expect(error).toMatchObject({
    status: 422,
    code: 'INSTALLMENT_NOT_ELIGIBLE',
    detail: 'Not eligible',
    installmentIds: ['P1'],
  });
});

test('throws a NETWORK_ERROR when the backend cannot be reached', async () => {
  server.use(http.get('/api/things', () => HttpResponse.error()));

  await expect(apiGet('/things')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
});

test('throws a NETWORK_ERROR when the proxy cannot reach the backend', async () => {
  server.use(http.get('/api/things', () => new HttpResponse('Bad Gateway', { status: 502 })));

  await expect(apiGet('/things')).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
});

test('lets a cancelled request fail with the AbortError instead of a network error', async () => {
  server.use(
    http.get('/api/things', async () => {
      await delay('infinite');
      return HttpResponse.json({});
    }),
  );
  const controller = new AbortController();

  const request = apiGet('/things', { signal: controller.signal });
  controller.abort();

  await expect(request).rejects.toMatchObject({ name: 'AbortError' });
});
