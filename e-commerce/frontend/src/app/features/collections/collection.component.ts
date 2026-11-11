import { Component, inject, OnInit, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Category, PageResponse, Product } from '../../core/models';
import { formatBdt, productImage } from '../../core/format';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-collection',
  imports: [FormsModule, RouterLink, TranslatePipe],
  templateUrl: './collection.component.html',
})
export class CollectionComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  readonly category = signal<Category | null>(null);
  readonly page = signal<PageResponse<Product> | null>(null);
  readonly loading = signal(true);
  readonly formatBdt = formatBdt;
  readonly productImage = productImage;

  q = '';
  sort = 'newest';
  sizeLabel = '';
  minPrice?: number;
  maxPrice?: number;
  pageIndex = 0;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug');
      if (!slug) return;
      this.loadCategory(slug);
      this.loadProducts(slug);
    });
  }

  apply(): void {
    this.pageIndex = 0;
    this.loadProducts(this.category()?.slug ?? '');
  }

  goToPage(page: number): void {
    this.pageIndex = page;
    this.loadProducts(this.category()?.slug ?? '');
    if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  private loadCategory(slug: string): void {
    this.api.getCategory(slug).subscribe({
      next: (category) => {
        this.category.set(category);
        this.title.setTitle(`${category.name} | Stitch & Style`);
        this.meta.updateTag({ name: 'description', content: category.description ?? `Shop ${category.name} at Stitch & Style.` });
      },
      error: () => {},
    });
  }

  private loadProducts(slug: string): void {
    this.loading.set(true);
    this.api.getCategoryProducts(slug, {
      q: this.q || undefined,
      sort: this.sort,
      sizeLabel: this.sizeLabel || undefined,
      minPrice: this.minPrice,
      maxPrice: this.maxPrice,
      page: this.pageIndex,
      size: 12,
    }).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
