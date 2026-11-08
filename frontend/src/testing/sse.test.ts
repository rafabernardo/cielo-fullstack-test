import { sse } from 'msw';
import { expect, test } from 'vitest';
import { server } from './server';

// Proves the test setup can stream Server-Sent Events, which the operation tracking relies on.
test('EventSource receives a status event from an MSW sse handler', async () => {
  server.use(
    sse<{ status: string }>('/api/portability-operations/:id/events', ({ client }) => {
      client.send({ event: 'status', data: JSON.stringify({ status: 'CONFIRMED' }) });
    }),
  );

  const source = new EventSource('/api/portability-operations/op-1/events');
  const data = await new Promise<string>((resolve) =>
    source.addEventListener('status', (event) => resolve(event.data)),
  );
  source.close();

  expect(JSON.parse(data)).toEqual({ status: 'CONFIRMED' });
});
