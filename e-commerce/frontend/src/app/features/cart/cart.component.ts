import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from '../../core/cart.service';
import { formatBdt } from '../../core/format';
import { SHOP_LINKS } from '../../core/shops';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, TranslatePipe],
  templateUrl: './cart.component.html',
})
export class CartComponent {
  readonly cart = inject(CartService);
  readonly lines = computed(() => this.cart.cart().lines);
  readonly allSelected = computed(() => this.lines().length > 0 && this.lines().every((line) => line.selected));
  readonly formatBdt = formatBdt;
  readonly shops = SHOP_LINKS;

  toggleAll(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.cart.toggleAll(checked);
  }

  changeQuantity(variantId: number, quantity: number): void {
    this.cart.updateQuantity(variantId, quantity);
  }

  remove(variantId: number): void {
    this.cart.remove(variantId);
  }
}
