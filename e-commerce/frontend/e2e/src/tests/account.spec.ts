import { CUSTOMER_EMAIL, CUSTOMER_STATE } from '../config';
import { expect, test } from '../fixtures';

test.use({ storageState: CUSTOMER_STATE });

/** Signed-in customer journeys. The session is a real Spring Security session. */
test.describe('customer account', () => {
  test('shows the signed-in profile, orders and returns tabs', async ({ account, page }) => {
    await account.goto();
    await account.expectSignedIn('E2E Customer', CUSTOMER_EMAIL);

    await expect(page.getByRole('tab', { name: /orders/i })).toBeVisible();
    await expect(page.getByText('No orders yet.')).toBeVisible();

    await account.openReturns();
  });

  test('signed-in customers can open support messages', async ({ page }) => {
    await page.goto('/messages');
    await expect(page.getByRole('heading', { name: 'Messages', level: 1 })).toBeVisible();
    await expect(page.getByText('Start a conversation')).toBeVisible();
  });
});
