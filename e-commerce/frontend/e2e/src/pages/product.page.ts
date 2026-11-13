import { expect, type Page } from '@playwright/test';

export class ProductPage {
  constructor(readonly page: Page) {}

  async goto(slug: string): Promise<void> {
    await this.page.goto(`/products/${slug}`);
  }

  async expectLoaded(name: string): Promise<void> {
    await expect(this.page.getByRole('heading', { name, level: 1 })).toBeVisible();
  }

  sizeWarning() {
    return this.page.getByText('Please select a size before adding this item to your cart.');
  }

  async selectSize(label: string): Promise<void> {
    await this.page.getByRole('button', { name: label, exact: true }).click();
  }

  async addToCart(): Promise<void> {
    await this.page.getByRole('button', { name: 'Add to cart', exact: true }).click();
  }

  async expectAddedToCart(): Promise<void> {
    await expect(this.page.getByText('Added to your cart.')).toBeVisible();
  }

  get cartBadge() {
    return this.page.locator('a[href="/cart"] .badge').first();
  }
}
