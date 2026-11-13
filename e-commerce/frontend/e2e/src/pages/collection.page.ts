import { expect, type Page } from '@playwright/test';

export class CollectionPage {
  constructor(readonly page: Page) {}

  async goto(slug: string): Promise<void> {
    await this.page.goto(`/collections/${slug}`);
  }

  async expectLoaded(name: string): Promise<void> {
    await expect(this.page.getByRole('heading', { name, level: 1 })).toBeVisible();
  }

  productLink(slug: string) {
    return this.page.locator(`a[href="/products/${slug}"]`).first();
  }

  get productCards() {
    return this.page.locator('a[href^="/products/"]');
  }
}
