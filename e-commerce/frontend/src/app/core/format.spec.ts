import { formatBdt, productImage } from './format';

describe('format helpers', () => {
  it('formats amounts as Bangladeshi taka with two decimals', () => {
    expect(formatBdt(0)).toBe('৳0');
    expect(formatBdt(1234.5)).toBe('৳1,234.5');
    expect(formatBdt('2500')).toBe('৳2,500');
  });

  it('treats missing or invalid amounts as zero', () => {
    expect(formatBdt(null)).toBe('৳0');
    expect(formatBdt(undefined)).toBe('৳0');
  });

  it('returns the first product image or the placeholder', () => {
    expect(productImage({ images: [{ url: '/media/a.png' }] })).toBe('/media/a.png');
    expect(productImage({ images: [] })).toBe('/placeholder.svg');
    expect(productImage({})).toBe('/placeholder.svg');
  });
});
