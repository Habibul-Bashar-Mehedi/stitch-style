import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { CartService } from './cart.service';
import { Cart, Me } from './models';

const emptyCart: Cart = { lines: [], subtotal: 0, deliveryCharge: 0, discountTotal: 0, grandTotal: 0 };

describe('CartService', () => {
  let api: any;
  let me: any;
  let cart: CartService;

  beforeEach(() => {
    localStorage.clear();
    api = {
      priceCart: vi.fn(() => of(emptyCart)),
      getCart: vi.fn(() => of(emptyCart)),
      replaceCart: vi.fn(() => of(emptyCart)),
      removeCartLine: vi.fn(() => of(emptyCart)),
      mergeCart: vi.fn(() => of(emptyCart)),
    };
    me = signal<Me | null>(null);
    TestBed.configureTestingModule({
      providers: [
        CartService,
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: { me } },
      ],
    });
    cart = TestBed.inject(CartService);
  });

  it('starts empty and counts only selected quantities', () => {
    expect(cart.cart().lines).toEqual([]);
    cart.cart.set({
      ...emptyCart,
      lines: [
        { variantId: 1, productId: 1, productSlug: 'a', productName: 'A', sizeLabel: 'M', quantity: 2, selected: true, unitPrice: 10, lineTotal: 20, available: 5, priceChanged: false, stockChanged: false },
        { variantId: 2, productId: 2, productSlug: 'b', productName: 'B', sizeLabel: 'L', quantity: 3, selected: false, unitPrice: 10, lineTotal: 30, available: 5, priceChanged: false, stockChanged: false },
      ],
    });

    expect(cart.count()).toBe(2);
    expect(cart.selectedLines().length).toBe(1);
    expect(cart.selectedCount()).toBe(1);
  });

  it('adds, merges quantities, updates and toggles in guest mode', () => {
    cart.add(1, 2);
    cart.add(1, 1);
    cart.add(2);

    expect(cart.local()).toEqual([
      { variantId: 1, quantity: 3, selected: true },
      { variantId: 2, quantity: 1, selected: true },
    ]);
    expect(localStorage.getItem('stitchstyle.cart.v1')).toContain('"variantId":1');

    cart.updateQuantity(1, 0);
    expect(cart.local()[0].quantity).toBe(1);

    cart.toggle(1, false);
    expect(cart.local()[0].selected).toBe(false);
    cart.toggleAll(true);
    expect(cart.local().every((item) => item.selected)).toBe(true);
  });

  it('prices guest carts through the public endpoint', () => {
    const priced: Cart = { ...emptyCart, subtotal: 100, grandTotal: 100 };
    api.priceCart.mockReturnValue(of(priced));

    cart.add(1, 1);

    expect(api.priceCart).toHaveBeenCalledWith([{ variantId: 1, quantity: 1, selected: true }]);
    expect(cart.cart()).toBe(priced);
  });

  it('falls back to an empty cart when public pricing fails', () => {
    api.priceCart.mockReturnValue(throwError(() => new Error('offline')));

    cart.add(1, 1);

    expect(cart.cart().lines).toEqual([]);
    expect(cart.cart().grandTotal).toBe(0);
  });

  it('removes guest lines and re-prices', () => {
    cart.add(1, 1);
    api.priceCart.mockClear();

    cart.remove(1);

    expect(cart.local()).toEqual([]);
    expect(api.priceCart).not.toHaveBeenCalled(); // empty carts short-circuit
    expect(localStorage.getItem('stitchstyle.cart.v1')).toBe('[]');
  });

  it('uses server cart endpoints when signed in', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    localStorage.setItem('stitchstyle.cart.v1', JSON.stringify([{ variantId: 5, quantity: 2, selected: true }]));
    const serverCart: Cart = {
      ...emptyCart,
      lines: [{ variantId: 5, productId: 1, productSlug: 'a', productName: 'A', sizeLabel: 'M', quantity: 2, selected: true, unitPrice: 50, lineTotal: 100, available: 5, priceChanged: false, stockChanged: false }],
      subtotal: 100,
      grandTotal: 100,
    };
    api.mergeCart.mockReturnValue(of(serverCart));

    cart.initialize();

    expect(api.mergeCart).toHaveBeenCalledWith([{ variantId: 5, quantity: 2, selected: true }]);
    expect(cart.cart()).toBe(serverCart);
    expect(cart.serverMode()).toBe(true);
    expect(cart.local()).toEqual([{ variantId: 5, quantity: 2, selected: true }]);
    expect(localStorage.getItem('stitchstyle.cart.v1')).toBeNull();
  });

  it('loads the server cart when there is no guest cart', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    api.getCart.mockReturnValue(of({ ...emptyCart, subtotal: 50 }));

    cart.initialize();

    expect(api.getCart).toHaveBeenCalled();
    expect(cart.cart().subtotal).toBe(50);
  });

  it('falls back to loading the server cart when a merge fails', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    localStorage.setItem('stitchstyle.cart.v1', JSON.stringify([{ variantId: 5, quantity: 2, selected: true }]));
    api.mergeCart.mockReturnValue(throwError(() => new Error('no')));
    api.getCart.mockReturnValue(of({ ...emptyCart, subtotal: 77 }));

    cart.initialize();

    expect(api.getCart).toHaveBeenCalled();
    expect(cart.cart().subtotal).toBe(77);
  });

  it('writes server mutations through replace/remove endpoints', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    cart.serverMode.set(true);
    cart.cart.set({
      ...emptyCart,
      lines: [{ variantId: 5, productId: 1, productSlug: 'a', productName: 'A', sizeLabel: 'M', quantity: 2, selected: true, unitPrice: 50, lineTotal: 100, available: 5, priceChanged: false, stockChanged: false }],
    });

    cart.add(5, 1);
    expect(api.replaceCart).toHaveBeenCalled();

    cart.remove(5);
    expect(api.removeCartLine).toHaveBeenCalledWith(5);

    cart.removeSelected();
    expect(api.replaceCart).toHaveBeenCalledTimes(2);

    cart.refresh();
    expect(api.getCart).toHaveBeenCalled();
  });

  it('subscribes to public pricing when refreshing a guest cart', () => {
    cart.local.set([{ variantId: 1, quantity: 1, selected: true }]);
    cart.refresh();
    expect(api.priceCart).toHaveBeenCalled();
  });

  it('resets to an empty cart when the server read fails', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    api.getCart.mockReturnValue(throwError(() => new Error('down')));

    cart.initialize();

    expect(cart.cart().lines).toEqual([]);
  });
});
