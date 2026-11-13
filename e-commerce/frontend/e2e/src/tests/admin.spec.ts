import { ADMIN_STATE } from '../config';
import { expect, test } from '../fixtures';

test.use({ storageState: ADMIN_STATE });

/** Back-office journeys with a real ADMIN session. */
test.describe('admin back office', () => {
  test('dashboard and back-office pages load real data', async ({ admin, page }) => {
    await admin.goto();
    await admin.expectDashboard();

    await expect(page.getByText('ADMIN', { exact: true })).toBeVisible();
    await expect(admin.apiDocsLink()).toBeVisible();

    await page.goto('/admin/catalog');
    await admin.expectPage('Products & collections');
    await page.getByRole('tab', { name: /products & stock/i }).click();
    await expect(page.getByText('Classic Cotton Panjabi').first()).toBeVisible();

    await page.goto('/admin/orders');
    await admin.expectPage('Orders');
    await expect(page.getByRole('button', { name: 'Apply filters' })).toBeVisible();

    await page.goto('/admin/banners');
    await admin.expectPage('Banners & notices');
  });

  test('the API docs link opens Swagger UI for an admin session', async ({ admin, context }) => {
    await admin.goto();

    const popupPromise = context.waitForEvent('page');
    await admin.apiDocsLink().click();
    const docs = await popupPromise;
    await docs.waitForLoadState('domcontentloaded');

    // The proxy on the storefront origin must forward /swagger-ui/** to the
    // backend, and the backend must accept the ADMIN session cookie.
    await expect(docs.locator('#swagger-ui')).toBeVisible();
    await expect(docs.getByText('Stitch & Style API').first()).toBeVisible();
  });
});
