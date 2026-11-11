import { computed, inject, Injectable, signal } from '@angular/core';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { Cart, CartInput, CartLine } from './models';

const STORAGE_KEY = 'stitchstyle.cart.v1';

const EMPTY: Cart = {
  lines: [],
  subtotal: 0,
  deliveryCharge: 0,
  discountTotal: 0,
  grandTotal: 0,
};

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly cart = signal<Cart>(EMPTY);
  readonly local = signal<CartInput[]>([]);
  readonly serverMode = signal(false);

  readonly count = computed(() =>
    this.cart().lines.filter((line) => line.selected).reduce((sum, line) => sum + line.quantity, 0),
  );
  readonly selectedLines = computed(() => this.cart().lines.filter((line) => line.selected));
  readonly selectedCount = computed(() => this.selectedLines().length);

  initialize(): void {
    const stored = this.readLocal();
    this.local.set(stored);
    if (this.auth.me()) {
      this.serverMode.set(true);
      if (stored.length > 0) {
        this.api.mergeCart(stored).subscribe({
          next: (cart) => {
            this.applyServerCart(cart);
            this.clearLocalStorage();
          },
          error: () => this.loadServer(),
        });
      } else {
        this.loadServer();
      }
    } else {
      this.priceLocal();
    }
  }

  add(variantId: number, quantity = 1): void {
    const items = [...this.local()];
    const existing = items.find((item) => item.variantId === variantId);
    if (existing) {
      existing.quantity += quantity;
      existing.selected = true;
    } else {
      items.push({ variantId, quantity, selected: true });
    }
    this.local.set(items);
    this.persist();
  }

  updateQuantity(variantId: number, quantity: number): void {
    const items = this.local().map((item) =>
      item.variantId === variantId ? { ...item, quantity: Math.max(1, quantity) } : item,
    );
    this.local.set(items);
    this.persist();
  }

  toggle(variantId: number, selected: boolean): void {
    const items = this.local().map((item) => (item.variantId === variantId ? { ...item, selected } : item));
    this.local.set(items);
    this.persist();
  }

  toggleAll(selected: boolean): void {
    this.local.set(this.local().map((item) => ({ ...item, selected })));
    this.persist();
  }

  remove(variantId: number): void {
    const remaining = this.local().filter((item) => item.variantId !== variantId);
    this.local.set(remaining);
    this.cart.set({ ...this.cart(), lines: this.cart().lines.filter((line) => line.variantId !== variantId) });
    if (this.serverMode()) {
      // Dedicated endpoint: removing the last line must not send an empty
      // replace payload, and one bad line must not block the removal.
      this.api.removeCartLine(variantId).subscribe({
        next: (cart) => this.applyServerCart(cart),
        error: () => this.refresh(),
      });
    } else {
      this.writeLocal(remaining);
      this.priceLocal();
    }
  }

  removeSelected(): void {
    const selectedIds = new Set(this.selectedLines().map((line) => line.variantId));
    const remaining = this.local().filter((item) => !selectedIds.has(item.variantId));
    this.local.set(remaining);
    this.cart.set({ ...this.cart(), lines: this.cart().lines.filter((line) => !selectedIds.has(line.variantId)) });
    if (this.serverMode()) {
      this.api.replaceCart(remaining).subscribe({
        next: (cart) => this.applyServerCart(cart),
        error: () => this.refresh(),
      });
    } else {
      this.writeLocal(remaining);
      this.priceLocal();
    }
  }

  refresh(): void {
    if (this.serverMode()) this.loadServer();
    else this.priceLocal();
  }

  private persist(): void {
    if (this.serverMode()) {
      this.api.replaceCart(this.local()).subscribe({
        next: (cart) => this.applyServerCart(cart),
        error: () => this.refresh(),
      });
    } else {
      this.writeLocal(this.local());
      this.priceLocal();
    }
  }

  private priceLocal(): void {
    const items = this.local();
    if (items.length === 0) {
      this.cart.set(EMPTY);
      return;
    }
    this.api.priceCart(items).subscribe({
      next: (cart) => this.cart.set(cart),
      error: () => this.cart.set(EMPTY),
    });
  }

  private loadServer(): void {
    this.api.getCart().subscribe({
      next: (cart) => this.applyServerCart(cart),
      error: () => this.cart.set(EMPTY),
    });
  }

  private applyServerCart(cart: Cart): void {
    this.cart.set(cart);
    this.local.set(cart.lines.map((line) => this.toInput(line)));
  }

  private toInput(line: CartLine): CartInput {
    return { variantId: line.variantId, quantity: line.quantity, selected: line.selected };
  }

  private readLocal(): CartInput[] {
    if (typeof localStorage === 'undefined') return [];
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      if (!Array.isArray(parsed)) return [];
      return parsed
        .filter((item) => item && typeof item.variantId === 'number' && typeof item.quantity === 'number')
        .map((item) => ({
          variantId: item.variantId,
          quantity: Math.max(1, item.quantity),
          selected: item.selected !== false,
        }));
    } catch {
      return [];
    }
  }

  private writeLocal(items: CartInput[]): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  }

  private clearLocalStorage(): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(STORAGE_KEY);
  }
}
