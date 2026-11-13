import { defineConfig, devices } from '@playwright/test';

/**
 * E2E configuration for the Stitch & Style storefront + back office.
 *
 * The stack itself is started by `e2e/setup-e2e.sh` on remapped ports
 * (backend 38134, SSR storefront 4100) against a dedicated `stitchstyle_e2e`
 * database, so the suite never touches the developer's data. Sign-in state is
 * created in `global-setup.ts` through the dev-only session endpoint.
 */
export default defineConfig({
  testDir: './e2e/src/tests',
  globalSetup: './e2e/global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4100',
    // Video for every test, not just failures.
    video: 'on',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
