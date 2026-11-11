/** Storefront shop entry points used by empty states and other CTAs. */
export interface ShopLink {
  slug: string;
  labelKey: string;
}

export const SHOP_LINKS: ShopLink[] = [
  { slug: 'men', labelKey: 'home.shopMen' },
  { slug: 'women', labelKey: 'home.shopWomen' },
  { slug: 'jewellery', labelKey: 'home.shopJewellery' },
];
