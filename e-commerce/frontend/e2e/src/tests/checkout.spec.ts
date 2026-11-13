import { expect, test } from '../fixtures';

/** Guest checkout against the real order, inventory and tracking APIs. */
test.describe('guest checkout', () => {
  test('places a COD order and tracks it with the confirmation token', async ({
    product,
    cart,
    checkout,
    tracking,
    page,
  }) => {
    await product.goto('classic-cotton-panjabi');
    await product.expectLoaded('Classic Cotton Panjabi');
    await product.selectSize('M');
    await product.addToCart();
    await product.expectAddedToCart();

    await cart.goto();
    await cart.expectItem('Classic Cotton Panjabi');
    await cart.checkoutSelected();
    await checkout.expectLoaded();

    await checkout.fillCustomer({
      name: 'Playwright Guest',
      phone: '01712345678',
      address: 'House 1, Road 1, Dhanmondi, Dhaka 1205',
    });
    await checkout.placeOrder();

    const orderNumber = await checkout.orderNumber();
    expect(orderNumber).toMatch(/^ORD-\d{8}-[A-Z0-9]+$/);

    await tracking.followConfirmationLink();
    await tracking.expectOrder(orderNumber);
    await expect(page.getByText('PENDING').first()).toBeVisible();
  });

  test('unknown order numbers show a friendly tracking error', async ({ tracking, page }) => {
    await tracking.goto();
    await tracking.trackByPhone('ORD-20260101-000000', '01712345678');

    await expect(page.getByText('No order matches that order number and phone number.')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Track your order' })).toBeVisible();
  });
});
