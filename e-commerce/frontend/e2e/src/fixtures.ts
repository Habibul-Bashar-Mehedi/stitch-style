import { test as base } from '@playwright/test';

import { AccountPage } from './pages/account.page';
import { AdminPage } from './pages/admin.page';
import { CartPage } from './pages/cart.page';
import { CheckoutPage } from './pages/checkout.page';
import { CollectionPage } from './pages/collection.page';
import { HomePage } from './pages/home.page';
import { ProductPage } from './pages/product.page';
import { TrackingPage } from './pages/tracking.page';

interface PageFixtures {
  home: HomePage;
  collection: CollectionPage;
  product: ProductPage;
  cart: CartPage;
  checkout: CheckoutPage;
  tracking: TrackingPage;
  account: AccountPage;
  admin: AdminPage;
}

/**
 * Page-object fixtures. Specs import `test`/`expect` from here, never from
 * `@playwright/test`, so every test gets the same verified selectors.
 */
export const test = base.extend<PageFixtures>({
  home: async ({ page }, use) => use(new HomePage(page)),
  collection: async ({ page }, use) => use(new CollectionPage(page)),
  product: async ({ page }, use) => use(new ProductPage(page)),
  cart: async ({ page }, use) => use(new CartPage(page)),
  checkout: async ({ page }, use) => use(new CheckoutPage(page)),
  tracking: async ({ page }, use) => use(new TrackingPage(page)),
  account: async ({ page }, use) => use(new AccountPage(page)),
  admin: async ({ page }, use) => use(new AdminPage(page)),
});

export { expect } from '@playwright/test';
