import { expect, type Page } from '@playwright/test';

export class AccountPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/account');
  }

  async expectSignedIn(fullName: string, email: string): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'My account', level: 1 })).toBeVisible();
    const main = this.page.locator('#main');
    await expect(main.getByText(fullName)).toBeVisible();
    await expect(main.getByText(email)).toBeVisible();
  }

  async openReturns(): Promise<void> {
    await this.page.getByRole('tab', { name: /returns/i }).click();
    await expect(this.page.getByText('No returns yet.')).toBeVisible();
  }
}
