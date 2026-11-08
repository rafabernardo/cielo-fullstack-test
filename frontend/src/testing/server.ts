import { setupServer } from 'msw/node';

// Each test registers its own handlers with server.use(...).
export const server = setupServer();
