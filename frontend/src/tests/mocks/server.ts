import { setupServer } from 'msw/node';

import { handlers } from './handlers';

/**
 * MSW server for Node.js environment (Vitest tests)
 * 
 * This server intercepts HTTP requests during tests and
 * returns mocked responses based on the defined handlers.
 */
export const server = setupServer(...handlers);
