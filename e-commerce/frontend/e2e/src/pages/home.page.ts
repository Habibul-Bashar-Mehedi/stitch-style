import { expect, type Page } from '@playwright/test';

export class HomePage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/');
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveTitle(/Stitch & Style/i);
    await expect(this.page.getByRole('heading', { name: /shop by collection/i })).toBeVisible();
    await expect(this.page.getByRole('heading', { name: /latest arrivals/i })).toBeVisible();
  }

  collectionCard(slug: string) {
    return this.page.locator(`a[href="/collections/${slug}"]`).first();
  }

  productCard(slug: string) {
    return this.page.locator(`a[href="/products/${slug}"]`).first();
  }
}
