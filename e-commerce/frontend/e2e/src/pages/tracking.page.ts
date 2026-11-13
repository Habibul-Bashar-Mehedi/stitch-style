import { expect, type Page } from '@playwright/test';

export class TrackingPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/track');
  }

  /** Follows the confirmation-page link, which carries the opaque token. */
  async followConfirmationLink(): Promise<void> {
    await this.page.getByRole('link', { name: 'Track this order' }).click();
    await expect(this.page).toHaveURL(/\/track\?token=/);
  }

  async trackByPhone(orderNumber: string, phone: string): Promise<void> {
    await this.goto();
    await this.page.getByPlaceholder('ORD-20261109-7K3M9Q').fill(orderNumber);
    await this.page.getByPlaceholder('01712345678').fill(phone);
    await this.page.getByRole('button', { name: 'Track' }).nth(1).click();
  }

  async expectOrder(orderNumber: string): Promise<void> {
    await expect(this.page.getByText(orderNumber).first()).toBeVisible();
    await expect(this.page.getByText('Items').first()).toBeVisible();
  }
}
