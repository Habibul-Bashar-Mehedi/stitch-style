import AxeBuilder from '@axe-core/playwright';

import { expect, test } from '../fixtures';

/** Accessibility gate on the two highest-traffic screens. */
test.describe('accessibility', () => {
  test('home and product page pass serious axe checks', async ({ home, product, page }) => {
    await home.goto();
    await home.expectLoaded();
    const homeViolations = await new AxeBuilder({ page }).analyze();
    expect(
      homeViolations.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical'),
    ).toEqual([]);

    await product.goto('classic-cotton-panjabi');
    await product.expectLoaded('Classic Cotton Panjabi');
    const productViolations = await new AxeBuilder({ page }).analyze();
    expect(
      productViolations.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical'),
    ).toEqual([]);
  });
});
