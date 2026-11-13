import { expect, type Page } from '@playwright/test';

export class CartPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/cart');
  }

  async expectItem(name: string): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Cart', level: 1 })).toBeVisible();
    await expect(this.page.getByText(name).first()).toBeVisible();
  }

  async checkoutSelected(): Promise<void> {
    await this.page.getByRole('link', { name: /checkout selected items/i }).click();
  }
}
