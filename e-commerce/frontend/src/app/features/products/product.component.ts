import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { WishlistService } from '../../core/wishlist.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { Product, Review, Variant } from '../../core/models';
import { formatBdt } from '../../core/format';

@Component({
  selector: 'app-product',
  imports: [RouterLink, FormsModule, TranslatePipe],
  templateUrl: './product.component.html',
})
export class ProductComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cart = inject(CartService);
  private readonly wishlist = inject(WishlistService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);

  readonly product = signal<Product | null>(null);
  readonly selectedVariant = signal<Variant | null>(null);
  readonly warning = signal(false);
  readonly added = signal(false);
  readonly activeImage = signal<string | null>(null);
  readonly related = signal<Product[]>([]);
  readonly reviews = signal<Review[]>([]);
  readonly reviewMessage = signal<string | null>(null);
  reviewRating = 5;
  reviewComment = '';
  readonly formatBdt = formatBdt;

  readonly displayPrice = computed(() => {
    const variant = this.selectedVariant();
    const product = this.product();
    if (!product) return 0;
    return variant?.priceOverride ?? product.effectivePrice;
  });

  constructor() {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug');
      if (slug) this.load(slug);
    });
  }

  select(variant: Variant): void {
    if (variant.available <= 0) return;
    this.selectedVariant.set(variant);
    this.warning.set(false);
  }

  addToCart(): void {
    const variant = this.selectedVariant();
    if (!variant) {
      this.warning.set(true);
      if (typeof document !== 'undefined') {
        const selector = document.getElementById('size-selector');
        selector?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        selector?.focus();
      }
      return;
    }
    this.cart.add(variant.id, 1);
    this.added.set(true);
    setTimeout(() => this.added.set(false), 1800);
  }

  orderNow(): void {
    const variant = this.selectedVariant();
    if (!variant) {
      this.warning.set(true);
      return;
    }
    this.cart.add(variant.id, 1);
    this.router.navigateByUrl('/checkout');
  }

  imageUrl(): string {
    return this.activeImage() ?? this.product()?.images?.[0]?.url ?? '/placeholder.svg';
  }

  toggleWishlist(): void {
    const product = this.product();
    if (!product) return;
    this.wishlist.toggle({
      productId: product.id,
      slug: product.slug,
      name: product.name,
      price: product.effectivePrice,
      imageUrl: product.images?.[0]?.url,
    });
  }

  inWishlist(): boolean {
    return this.wishlist.contains(this.product()?.id ?? 0);
  }

  submitReview(): void {
    const product = this.product();
    if (!product) return;
    this.api.submitReview(product.slug, this.reviewRating, this.reviewComment).subscribe({
      next: () => {
        this.reviewMessage.set('Review submitted for moderation.');
        this.reviewComment = '';
      },
      error: (err) => this.reviewMessage.set(err?.error?.detail ?? 'Could not submit the review.'),
    });
  }

  private setJsonLd(product: Product): void {
    if (!this.document) return;
    this.document.getElementById('product-jsonld')?.remove();
    const script = this.document.createElement('script');
    script.id = 'product-jsonld';
    script.type = 'application/ld+json';
    script.textContent = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: product.name,
      description: product.description,
      sku: product.variants?.[0]?.sku,
      brand: { '@type': 'Brand', name: product.brand || 'Stitch & Style' },
      offers: {
        '@type': 'Offer',
        priceCurrency: 'BDT',
        price: product.effectivePrice,
        availability: product.variants?.some((variant) => variant.available > 0)
          ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
        url: `${this.document.location?.origin ?? ''}/products/${product.slug}`,
      },
    });
    this.document.head.appendChild(script);
  }

  private load(slug: string): void {
    this.api.getProduct(slug).subscribe({
      next: (product) => {
        this.product.set(product);
        this.activeImage.set(product.images?.[0]?.url ?? null);
        this.title.setTitle(`${product.name} | Stitch & Style`);
        this.meta.updateTag({ name: 'description', content: product.description });
        this.setJsonLd(product);
        this.api.relatedProducts(slug).subscribe({ next: (related) => this.related.set(related) });
        this.api.productReviews(slug).subscribe({ next: (reviews) => this.reviews.set(reviews) });
      },
      error: () => {},
    });
  }
}
