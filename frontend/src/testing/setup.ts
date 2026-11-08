import '@testing-library/jest-dom/vitest';
import { EventSource } from 'eventsource';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

// Neither Node 22 (without a flag) nor jsdom provides EventSource; MSW's sse() intercepts this one.
globalThis.EventSource ??= EventSource as unknown as typeof globalThis.EventSource;

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
