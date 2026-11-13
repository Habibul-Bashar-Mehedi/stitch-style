import { I18nService } from './i18n.service';

describe('I18nService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'en';
  });

  it('defaults to English and returns dictionary entries', () => {
    const i18n = new I18nService();
    expect(i18n.lang()).toBe('en');
    expect(i18n.isBangla()).toBe(false);
    expect(i18n.t('nav.cart')).toBe('Cart');
    expect(i18n.t('missing.key')).toBe('missing.key');
  });

  it('switches to Bangla and persists the choice', () => {
    const i18n = new I18nService();
    i18n.set('bn');
    expect(i18n.lang()).toBe('bn');
    expect(i18n.isBangla()).toBe(true);
    expect(i18n.t('nav.cart')).toBe('কার্ট');
    expect(localStorage.getItem('stitchstyle.lang')).toBe('bn');
    expect(document.documentElement.lang).toBe('bn');
  });

  it('toggles between the two languages', () => {
    const i18n = new I18nService();
    i18n.toggle();
    expect(i18n.lang()).toBe('bn');
    i18n.toggle();
    expect(i18n.lang()).toBe('en');
  });

  it('reads a stored language preference', () => {
    localStorage.setItem('stitchstyle.lang', 'bn');
    expect(new I18nService().lang()).toBe('bn');
    localStorage.setItem('stitchstyle.lang', 'fr');
    expect(new I18nService().lang()).toBe('en');
  });
});
