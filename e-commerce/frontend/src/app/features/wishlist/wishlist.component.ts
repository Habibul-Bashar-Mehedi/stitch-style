import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WishlistService } from '../../core/wishlist.service';
import { formatBdt } from '../../core/format';
import { SHOP_LINKS } from '../../core/shops';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-wishlist',
  imports: [RouterLink, TranslatePipe],
  templateUrl: './wishlist.component.html',
})
export class WishlistComponent {
  readonly wishlist = inject(WishlistService);
  readonly formatBdt = formatBdt;
  readonly shops = SHOP_LINKS;
}
