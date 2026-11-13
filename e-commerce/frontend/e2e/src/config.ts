/** Shared E2E constants. Ports are remapped so the suite never collides with dev services. */
export const FRONTEND_PORT = process.env['E2E_FRONTEND_PORT'] ?? '4100';
export const BACKEND_PORT = process.env['E2E_BACKEND_PORT'] ?? '38134';

export const BASE_URL = process.env['E2E_BASE_URL'] ?? `http://localhost:${FRONTEND_PORT}`;

export const ADMIN_EMAIL = 'e2e-admin@stitchandstyle.test';
export const CUSTOMER_EMAIL = 'e2e-customer@stitchandstyle.test';

/** Relative to the frontend directory (the Playwright working directory). */
export const ADMIN_STATE = 'e2e/.auth/admin.json';
export const CUSTOMER_STATE = 'e2e/.auth/customer.json';
