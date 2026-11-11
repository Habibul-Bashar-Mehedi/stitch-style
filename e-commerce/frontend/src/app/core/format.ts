export function formatBdt(value: number | string | null | undefined): string {
  const amount = Number(value ?? 0);
  return `৳${new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(amount)}`;
}

export function productImage(product: { images?: { url: string }[] }): string {
  return product.images?.[0]?.url ?? '/placeholder.svg';
}
