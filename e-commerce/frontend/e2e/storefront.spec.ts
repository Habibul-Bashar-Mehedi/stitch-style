import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('storefront', () => {
  test('home renders real catalogue content', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/Stitch & Style/);
    await expect(page.getByText('Shop by collection').first()).toBeVisible();
    await expect(page.getByText('Latest arrivals').first()).toBeVisible();
    await expect(page.locator('a[href*="/collections/men"]').first()).toBeVisible();
  });

  test('product size validation and guest checkout', async ({ page }) => {
    await page.goto('/products/classic-cotton-panjabi');
    await expect(page.getByRole('heading', { name: 'Classic Cotton Panjabi' })).toBeVisible();
    await expect(page.getByText('Please select a size')).toHaveCount(0);
    await page.getByRole('button', { name: 'Add to cart' }).click();
    await expect(page.getByText('Please select a size')).toBeVisible();
    await page.getByRole('button', { name: 'M', exact: true }).click();
    await page.getByRole('button', { name: 'Add to cart' }).click();
    await expect(page.getByText('Added to your cart.')).toBeVisible();
    await page.goto('/cart');
    await expect(page.getByText('Classic Cotton Panjabi').first()).toBeVisible();
    await page.getByRole('link', { name: /Checkout selected items/i }).click();
    await expect(page.getByRole('heading', { name: 'Checkout' })).toBeVisible();
    await page.getByPlaceholder('Nusrat Jahan').fill('Playwright Guest');
    await page.getByPlaceholder('01712345678').fill('01712345678');
    await page.getByPlaceholder('House, road, area, district').fill('House 1, Road 1, Dhanmondi, Dhaka 1205');
    await page.getByRole('button', { name: 'Place order' }).click();
    await expect(page.getByText('Order placed')).toBeVisible();
    await expect(page.getByText(/ORD-/)).toBeVisible();
  });

  test('language toggle switches the storefront to Bangla', async ({ page }) => {
    await page.goto('/');
    await page.getByTitle('Switch language').click();
    await expect(page.getByText('কালেকশন অনুযায়ী কিনুন').first()).toBeVisible();
    await page.getByTitle('Switch language').click();
    await expect(page.getByText('Shop by collection').first()).toBeVisible();
  });

  test('home and product pass serious axe checks', async ({ page }) => {
    await page.goto('/');
    const home = await new AxeBuilder({ page }).analyze();
    expect(home.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')).toEqual([]);
    await page.goto('/products/classic-cotton-panjabi');
    const product = await new AxeBuilder({ page }).analyze();
    expect(product.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')).toEqual([]);
  });
});
