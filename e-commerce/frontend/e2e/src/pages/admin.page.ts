import { expect, type Page } from '@playwright/test';

export class AdminPage {
  constructor(readonly page: Page) {}

  async goto(path = ''): Promise<void> {
    await this.page.goto(`/admin${path}`);
  }

  async expectDashboard(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
    await expect(this.page.getByRole('heading', { name: 'Top products' })).toBeVisible();
    await expect(this.page.getByRole('heading', { name: 'Status distribution' })).toBeVisible();
  }

  async expectPage(heading: string): Promise<void> {
    await expect(this.page.getByRole('heading', { name: heading, level: 1 })).toBeVisible();
  }

  apiDocsLink() {
    return this.page.getByRole('link', { name: 'API docs' });
  }

  navLink(name: string) {
    return this.page.getByRole('link', { name, exact: true });
  }
}
