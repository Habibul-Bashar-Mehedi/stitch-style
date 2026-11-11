import { Component, computed, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Home, Product } from '../../core/models';
import { formatBdt, productImage } from '../../core/format';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-home',
  imports: [RouterLink, TranslatePipe],
  templateUrl: './home.component.html',
})
export class HomeComponent {
  private readonly api = inject(ApiService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  readonly home = signal<Home | null>(null);
  readonly hero = computed(() => this.home()?.banners?.[0] ?? null);
  readonly formatBdt = formatBdt;
  readonly productImage = productImage;

  constructor() {
    this.title.setTitle('Stitch & Style — Dynamic fashion for Bangladesh');
    this.meta.updateTag({ name: 'description', content: 'Shop dynamic fashion collections in Bangladesh with cash on delivery, local delivery areas and secure order tracking.' });
    this.api.getHome().subscribe({
      next: (home) => this.home.set(home),
      error: () => {},
    });
  }

  productLink(product: Product): string {
    return `/products/${product.slug}`;
  }
}
