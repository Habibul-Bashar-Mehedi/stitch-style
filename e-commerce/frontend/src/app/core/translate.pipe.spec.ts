import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';
import { TranslatePipe } from './translate.pipe';
import { SHOP_LINKS } from './shops';

describe('TranslatePipe', () => {
  let pipe: TranslatePipe;
  let i18n: I18nService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [TranslatePipe] });
    pipe = TestBed.inject(TranslatePipe);
    i18n = TestBed.inject(I18nService);
  });

  it('translates the active language and falls back to the key', () => {
    expect(pipe.transform('nav.cart')).toBe('Cart');
    expect(pipe.transform('nope')).toBe('nope');

    i18n.set('bn');
    expect(pipe.transform('nav.cart')).toBe('কার্ট');
  });
});

describe('SHOP_LINKS', () => {
  it('contains the three storefront entry points with translation keys', () => {
    expect(SHOP_LINKS.map((shop) => shop.slug)).toEqual(['men', 'women', 'jewellery']);
    expect(SHOP_LINKS.every((shop) => shop.labelKey.startsWith('home.shop'))).toBe(true);
  });
});
