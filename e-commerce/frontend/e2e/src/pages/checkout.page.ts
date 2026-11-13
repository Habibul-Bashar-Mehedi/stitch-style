import { expect, type Page } from '@playwright/test';

export interface CheckoutCustomer {
  name: string;
  phone: string;
  address: string;
}

export class CheckoutPage {
  constructor(readonly page: Page) {}

  async expectLoaded(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Checkout', level: 1 })).toBeVisible();
  }

  async fillCustomer({ name, phone, address }: CheckoutCustomer): Promise<void> {
    await this.page.getByPlaceholder('Nusrat Jahan').fill(name);
    await this.page.getByPlaceholder('01712345678').fill(phone);
    await this.page.getByPlaceholder('House, road, area, district').fill(address);
  }

  async placeOrder(): Promise<void> {
    await this.page.getByRole('button', { name: 'Place order' }).click();
    await expect(this.page.getByRole('heading', { name: 'Order placed' })).toBeVisible();
  }

  async orderNumber(): Promise<string> {
    const text = await this.page.getByText(/^ORD-/).first().innerText();
    return text.trim();
  }
}
