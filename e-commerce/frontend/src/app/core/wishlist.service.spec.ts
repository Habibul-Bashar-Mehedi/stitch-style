import { WishlistService } from './wishlist.service';

const item = { productId: 1, slug: 'panjabi', name: 'Panjabi', price: 1200 };

describe('WishlistService', () => {
  beforeEach(() => localStorage.clear());

  it('adds and removes items and tracks the count', () => {
    const wishlist = new WishlistService();
    expect(wishlist.count()).toBe(0);

    wishlist.toggle(item);
    expect(wishlist.count()).toBe(1);
    expect(wishlist.contains(1)).toBe(true);

    wishlist.toggle(item);
    expect(wishlist.count()).toBe(0);
    expect(wishlist.contains(1)).toBe(false);
  });

  it('removes by product id', () => {
    const wishlist = new WishlistService();
    wishlist.toggle(item);
    wishlist.toggle({ ...item, productId: 2, slug: 'sharee' });

    wishlist.remove(1);

    expect(wishlist.count()).toBe(1);
    expect(wishlist.contains(2)).toBe(true);
  });

  it('persists and reloads the list', () => {
    const first = new WishlistService();
    first.toggle(item);

    const second = new WishlistService();
    expect(second.count()).toBe(1);
    expect(second.items()[0].slug).toBe('panjabi');
  });

  it('ignores corrupt stored data', () => {
    localStorage.setItem('stitchstyle.wishlist.v1', '{not json');
    expect(new WishlistService().items()).toEqual([]);

    localStorage.setItem('stitchstyle.wishlist.v1', '{"not":"an array"}');
    expect(new WishlistService().items()).toEqual([]);
  });
});
