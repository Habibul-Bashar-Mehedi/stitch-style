import { request } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

import { ADMIN_EMAIL, ADMIN_STATE, BASE_URL, CUSTOMER_EMAIL, CUSTOMER_STATE } from './src/config';

/**
 * Creates the two authenticated sessions used by the suite.
 *
 * Google OIDC cannot be driven headlessly, so the backend exposes
 * `POST /api/v1/dev/session` when dev tools are enabled. It establishes the
 * same server-side session that OIDC would, which means the tests still
 * exercise the real session cookie, CSRF contract and URL authorization.
 */
export default async function globalSetup(): Promise<void> {
  await mkdir('e2e/.auth', { recursive: true });

  const sessions = [
    { path: ADMIN_STATE, email: ADMIN_EMAIL, role: 'ADMIN', fullName: 'E2E Admin' },
    { path: CUSTOMER_STATE, email: CUSTOMER_EMAIL, role: 'CUSTOMER', fullName: 'E2E Customer' },
  ] as const;

  for (const session of sessions) {
    const context = await request.newContext({ baseURL: BASE_URL });
    const response = await context.post('/api/v1/dev/session', {
      data: { email: session.email, role: session.role, fullName: session.fullName },
    });
    if (!response.ok()) {
      throw new Error(
        `Dev sign-in failed for ${session.role} (${response.status()}). ` +
          `Start the stack with e2e/setup-e2e.sh first.\n${await response.text()}`,
      );
    }
    await context.storageState({ path: session.path });
    await context.dispose();
  }
}
