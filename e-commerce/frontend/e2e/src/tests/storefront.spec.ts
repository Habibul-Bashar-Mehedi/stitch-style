import { expect, test } from '../fixtures';

/** Public storefront journeys: no session, real catalogue and real APIs. */
test.describe('storefront', () => {
  test('home renders the real catalogue', async ({ home }) => {
    await home.goto();
    await home.expectLoaded();

    await expect(home.collectionCard('men')).toBeVisible();
    await expect(home.productCard('classic-cotton-panjabi')).toBeVisible();

    // Seeded notices render in the announcement strip.
    await expect(home.page.getByText('Eid delivery update').first()).toBeVisible();
  });

  test('browse a collection, open a product and add the selected size to the cart', async ({
    collection,
    product,
    cart,
    page,
  }) => {
    await collection.goto('men');
    await collection.expectLoaded('Men');
    await expect(collection.productCards.first()).toBeVisible();

    await collection.productLink('classic-cotton-panjabi').click();
    await expect(page).toHaveURL(/\/products\/classic-cotton-panjabi$/);
    await product.expectLoaded('Classic Cotton Panjabi');

    await product.addToCart();
    await expect(product.sizeWarning()).toBeVisible();

    await product.selectSize('M');
    await product.addToCart();
    await product.expectAddedToCart();
    await expect(product.cartBadge).toHaveText(/[1-9]/);

    await cart.goto();
    await cart.expectItem('Classic Cotton Panjabi');
  });

  test('language toggle switches to Bangla and back', async ({ home, page }) => {
    await home.goto();
    await home.expectLoaded();

    await page.getByTitle('Switch language').click();
    await expect(page.getByRole('heading', { name: 'কালেকশন অনুযায়ী কিনুন' })).toBeVisible();

    await page.getByTitle('Switch language').click();
    await expect(page.getByRole('heading', { name: /shop by collection/i })).toBeVisible();
  });

  test('consumer complaint is recorded', async ({ page }) => {
    await page.goto('/contact');
    await expect(page.getByRole('heading', { name: 'Raise a complaint' })).toBeVisible();

    await page.getByPlaceholder('Your name').fill('Playwright Customer');
    await page.getByPlaceholder('Mobile number').fill('01712345679');
    await page.getByPlaceholder('What went wrong?').fill('E2E: delivery address was unclear.');
    await page.getByRole('button', { name: 'Submit complaint' }).click();

    await expect(page.getByText(/your complaint has been recorded/i)).toBeVisible();
  });
});
