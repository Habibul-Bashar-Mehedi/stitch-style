import { computed, Injectable, signal } from '@angular/core';

export interface WishlistItem {
  productId: number;
  slug: string;
  name: string;
  price: number;
  imageUrl?: string;
}

const STORAGE_KEY = 'stitchstyle.wishlist.v1';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  readonly items = signal<WishlistItem[]>(this.read());
  readonly count = computed(() => this.items().length);

  toggle(item: WishlistItem): void {
    const exists = this.items().some((existing) => existing.productId === item.productId);
    const next = exists
      ? this.items().filter((existing) => existing.productId !== item.productId)
      : [...this.items(), item];
    this.items.set(next);
    this.write(next);
  }

  contains(productId: number): boolean {
    return this.items().some((item) => item.productId === productId);
  }

  remove(productId: number): void {
    const next = this.items().filter((item) => item.productId !== productId);
    this.items.set(next);
    this.write(next);
  }

  private read(): WishlistItem[] {
    if (typeof localStorage === 'undefined') return [];
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private write(items: WishlistItem[]): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  }
}
