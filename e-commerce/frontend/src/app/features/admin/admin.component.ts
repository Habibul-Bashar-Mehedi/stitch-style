import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Category, Product } from '../../core/models';
import { formatBdt } from '../../core/format';

interface VariantDraft {
  id?: number;
  sizeLabel: string;
  sku: string;
  stockQuantity: number;
  lowStockThreshold: number;
  active: boolean;
}

@Component({
  selector: 'app-admin',
  imports: [FormsModule, RouterLink],
  templateUrl: './admin.component.html',
})
export class AdminComponent {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly categories = signal<Category[]>([]);
  readonly products = signal<Product[]>([]);
  readonly message = signal<string | null>(null);
  readonly formatBdt = formatBdt;
  readonly sizeTypes = ['LETTER', 'WAIST', 'SHOE', 'FREE', 'CUSTOM'];

  // Layout state (presentation only): one workspace at a time, form on demand.
  readonly workspace = signal<'collections' | 'products'>('collections');
  readonly categoryFormOpen = signal(false);
  readonly productFormOpen = signal(false);

  readonly topLevelCollections = computed(() =>
    this.categories().filter((category) => category.parentId === null && category.slug !== 'archived-products'),
  );
  readonly nestedCategories = computed(() =>
    this.categories().filter((category) => category.parentId !== null),
  );
  readonly activeProductCount = computed(() => this.products().filter((product) => product.active).length);
  readonly featuredProductCount = computed(() => this.products().filter((product) => product.featured).length);
  readonly lowStockCount = computed(() =>
    this.products()
      .flatMap((product) => product.variants)
      .filter((variant) => variant.active && variant.available <= variant.lowStockThreshold).length,
  );

  // Category / collection form.
  categoryName = '';
  categorySlug = '';
  categoryDescription = '';
  categoryParentId: number | null = null;
  categoryImageMediaId: number | null = null;
  categoryImageUrl: string | null = null;
  categorySizeType = 'LETTER';
  categoryOnHome = true;
  categoryInNavbar = true;
  categoryActive = true;
  editingCategoryId: number | null = null;

  // Product form.
  productName = '';
  productSlug = '';
  productDescription = '';
  productPrice: number | null = null;
  productDiscountPrice: number | null = null;
  productGenderId: number | null = null;
  productCategoryId: number | null = null;
  productSizeType = 'LETTER';
  productActive = true;
  productFeatured = false;
  productImageMediaId: number | null = null;
  productImageUrl: string | null = null;
  productVariants: VariantDraft[] = [this.blankVariant()];
  editingProductId: number | null = null;
  productMessage = signal<string | null>(null);

  constructor() {
    if (this.auth.me()) {
      this.load();
    } else {
      this.auth.load().subscribe(() => this.load());
    }
  }

  load(): void {
    if (!this.auth.isStaff()) return;
    this.api.adminCategories().subscribe({
      next: (categories) => {
        const flat = this.flatten(categories);
        this.categories.set(flat);
        if (!this.productGenderId) {
          this.productGenderId = flat.find((category) =>
            category.parentId === null && category.slug !== 'archived-products')?.id ?? null;
        }
        if (!this.productCategoryId) {
          this.productCategoryId = this.firstCategoryForGender(this.productGenderId);
        }
      },
    });
    this.api.adminProducts().subscribe({ next: (page) => this.products.set(page.content) });
  }

  // ---- Workspace / form panels -------------------------------------------

  startCategoryForm(): void {
    this.workspace.set('collections');
    this.categoryFormOpen.set(true);
    this.resetCategory();
  }

  startProductForm(): void {
    this.workspace.set('products');
    this.productFormOpen.set(true);
    this.resetProduct();
  }

  closeCategoryForm(): void {
    this.categoryFormOpen.set(false);
    this.resetCategory();
  }

  closeProductForm(): void {
    this.productFormOpen.set(false);
    this.resetProduct();
  }

  // ---- Collections / categories -----------------------------------------

  categoryParentOptions(): Category[] {
    const editingId = this.editingCategoryId;
    if (editingId != null) {
      const editing = this.categories().find((category) => category.id === editingId);
      // A top-level collection (Men / Women) must stay top-level.
      if (editing && editing.parentId == null) return [];
    }
    // Only the storefront roots (Men / Women) can be a parent.
    return this.categories().filter(
      (category) => category.parentId === null && category.id !== editingId,
    );
  }

  saveCategory(): void {
    if (!this.categoryName.trim()) {
      this.message.set('Category name is required.');
      return;
    }
    const payload = {
      name: this.categoryName.trim(),
      slug: this.categorySlug.trim() || undefined,
      description: this.categoryDescription.trim() || null,
      parentId: this.categoryParentId,
      imageMediaId: this.categoryImageMediaId,
      sizeType: this.categorySizeType,
      active: this.categoryActive,
      showOnHome: this.categoryOnHome,
      showInNavbar: this.categoryInNavbar,
    };
    const call = this.editingCategoryId
      ? this.api.updateCategory(this.editingCategoryId, payload)
      : this.api.createCategory(payload);
    call.subscribe({
      next: () => {
        this.message.set(this.editingCategoryId ? 'Collection updated.' : 'Collection created.');
        this.categoryFormOpen.set(false);
        this.resetCategory();
        this.load();
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not save the collection.'),
    });
  }

  editCategory(category: Category): void {
    this.workspace.set('collections');
    this.categoryFormOpen.set(true);
    this.editingCategoryId = category.id;
    this.categoryName = category.name;
    this.categorySlug = category.slug;
    this.categoryDescription = category.description ?? '';
    this.categoryParentId = category.parentId;
    this.categoryImageMediaId = category.imageMediaId ?? null;
    this.categoryImageUrl = category.imageUrl ?? null;
    this.categorySizeType = category.sizeType ?? 'LETTER';
    this.categoryOnHome = category.showOnHome;
    this.categoryInNavbar = category.showInNavbar;
    this.categoryActive = category.active;
    this.scrollTo('category-form');
  }

  resetCategory(): void {
    this.editingCategoryId = null;
    this.categoryName = '';
    this.categorySlug = '';
    this.categoryDescription = '';
    this.categoryParentId = null;
    this.categoryImageMediaId = null;
    this.categoryImageUrl = null;
    this.categorySizeType = 'LETTER';
    this.categoryOnHome = true;
    this.categoryInNavbar = true;
    this.categoryActive = true;
    this.scrollTo('category-form');
  }

  uploadCategoryImage(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.uploadMedia(file).subscribe({
      next: (media) => {
        this.categoryImageMediaId = media.id;
        this.categoryImageUrl = media.url;
        this.message.set('Collection image uploaded. Save the collection to apply it.');
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not upload the image.'),
    });
  }

  toggle(category: Category): void {
    this.api.setCategoryVisibility(category.id, !category.active).subscribe({ next: () => this.load() });
  }

  deleteCategory(category: Category): void {
    if (!confirm(`Delete "${category.name}"? This cannot be undone.`)) return;
    this.api.deleteCategory(category.id).subscribe({
      next: () => {
        this.message.set(`"${category.name}" deleted.`);
        if (this.editingCategoryId === category.id) {
          this.categoryFormOpen.set(false);
          this.resetCategory();
        }
        if (this.productGenderId === category.id) this.productGenderId = null;
        this.load();
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not delete the collection.'),
    });
  }

  moveCategory(category: Category, delta: number): void {
    const siblings = this.categories()
      .filter((item) => item.parentId === category.parentId)
      .sort((a, b) => a.displayOrder - b.displayOrder);
    const index = siblings.findIndex((item) => item.id === category.id);
    const target = index + delta;
    if (index < 0 || target < 0 || target >= siblings.length) return;
    [siblings[index], siblings[target]] = [siblings[target], siblings[index]];
    this.api.reorderCategories(category.parentId, siblings.map((item) => item.id)).subscribe({ next: () => this.load() });
  }

  parentName(category: Category): string {
    return this.categories().find((item) => item.id === category.parentId)?.name ?? '';
  }

  /** "Jewellery" for a root-level product, "Men · Panjabi" for a nested one. */
  categoryLabel(product: Product): string {
    const category = this.categories().find((item) => item.id === product.categoryId);
    const rootName = this.rootOf(product.categoryId)?.name ?? '';
    if (!category || category.parentId === null) return rootName || product.categorySlug;
    return `${rootName} · ${category.name}`.trim();
  }

  // ---- Products ----------------------------------------------------------

  productGenderOptions(): Category[] {
    // Top-level collections are the genders (Men / Women). The hidden archive
    // container is not a gender and must never appear in the product form.
    return this.categories().filter((category) =>
      category.parentId === null && category.slug !== 'archived-products');
  }

  productCategoryOptions(): Category[] {
    const genderId = this.productGenderId;
    if (genderId == null) {
      return this.categories();
    }
    // The gender root stays selectable so products already placed directly on
    // Men / Women remain editable; its descendants follow underneath.
    return this.categories().filter((category) => this.isInGender(category, genderId));
  }

  onGenderChange(): void {
    if (!this.productCategoryOptions().some((category) => category.id === this.productCategoryId)) {
      this.productCategoryId = this.firstCategoryForGender(this.productGenderId);
    }
    this.onCategoryChange();
  }

  onCategoryChange(): void {
    const category = this.categories().find((item) => item.id === this.productCategoryId);
    if (category) {
      this.productSizeType = category.sizeType ?? 'LETTER';
    }
  }

  addVariant(): void {
    this.productVariants = [...this.productVariants, this.blankVariant()];
  }

  removeVariant(index: number): void {
    if (this.productVariants.length <= 1) return;
    this.productVariants = this.productVariants.filter((_, i) => i !== index);
  }

  saveProduct(): void {
    if (!this.productName.trim() || !this.productCategoryId || !this.productPrice || this.productPrice <= 0) {
      this.productMessage.set('Name, collection/category and a positive price are required.');
      return;
    }
    const baseSku = (this.productSlug.trim() || this.productName.trim())
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
    const variants = this.productVariants
      .filter((variant) => variant.sizeLabel.trim())
      .map((variant) => {
        const size = variant.sizeLabel.trim();
        const sku = (variant.sku.trim() || `${baseSku}-${size}`)
          .toUpperCase()
          .replace(/[^A-Z0-9]+/g, '-')
          .replace(/(^-|-$)/g, '');
        return {
          id: variant.id,
          sizeLabel: size,
          sku,
          stockQuantity: Number(variant.stockQuantity) || 0,
          lowStockThreshold: Number(variant.lowStockThreshold) || 2,
          active: variant.active,
        };
      });
    if (!variants.length) {
      this.productMessage.set('Add at least one size/variant.');
      return;
    }
    const payload = {
      categoryId: Number(this.productCategoryId),
      nameI18n: { en: this.productName.trim() },
      slug: this.productSlug.trim() || undefined,
      descriptionI18n: { en: this.productDescription.trim() },
      basePrice: this.productPrice,
      discountPrice: this.productDiscountPrice && this.productDiscountPrice > 0 ? this.productDiscountPrice : null,
      sizeType: this.productSizeType,
      active: this.productActive,
      featured: this.productFeatured,
      variants,
      imageMediaIds: this.productImageMediaId ? [this.productImageMediaId] : [],
    };
    const call = this.editingProductId
      ? this.api.updateProduct(this.editingProductId, payload)
      : this.api.createProduct(payload);
    call.subscribe({
      next: () => {
        this.productMessage.set(this.editingProductId ? 'Product updated.' : 'Product created.');
        this.productFormOpen.set(false);
        this.resetProduct();
        this.load();
      },
      error: (err) => this.productMessage.set(err?.error?.detail ?? 'Could not save the product.'),
    });
  }

  editProduct(product: Product): void {
    this.workspace.set('products');
    this.productFormOpen.set(true);
    this.editingProductId = product.id;
    this.productName = product.name;
    this.productSlug = product.slug;
    this.productDescription = product.description ?? '';
    this.productPrice = Number(product.basePrice);
    this.productDiscountPrice = product.discountPrice == null ? null : Number(product.discountPrice);
    this.productGenderId = this.rootOf(product.categoryId)?.id ?? null;
    this.productCategoryId = product.categoryId;
    this.productSizeType = product.sizeType ?? 'LETTER';
    this.productActive = product.active;
    this.productFeatured = product.featured;
    this.productImageMediaId = product.images?.[0]?.mediaId ?? null;
    this.productImageUrl = product.images?.[0]?.url ?? null;
    const activeVariants = product.variants.filter((variant) => variant.active);
    this.productVariants = activeVariants.length
      ? activeVariants.map((variant) => ({
          id: variant.id,
          sizeLabel: variant.sizeLabel,
          sku: variant.sku,
          stockQuantity: variant.stockQuantity,
          lowStockThreshold: variant.lowStockThreshold ?? 2,
          active: variant.active,
        }))
      : [this.blankVariant()];
    this.scrollTo('product-form');
  }

  resetProduct(): void {
    this.editingProductId = null;
    this.productName = '';
    this.productSlug = '';
    this.productDescription = '';
    this.productPrice = null;
    this.productDiscountPrice = null;
    this.productGenderId = this.productGenderOptions()[0]?.id ?? null;
    this.productCategoryId = this.firstCategoryForGender(this.productGenderId);
    this.productSizeType = 'LETTER';
    this.productActive = true;
    this.productFeatured = false;
    this.productImageMediaId = null;
    this.productImageUrl = null;
    this.productVariants = [this.blankVariant()];
    this.scrollTo('product-form');
  }

  uploadProductImage(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.uploadMedia(file).subscribe({
      next: (media) => {
        this.productImageMediaId = media.id;
        this.productImageUrl = media.url;
        this.productMessage.set('Product image uploaded. Save the product to apply it.');
      },
      error: (err) => this.productMessage.set(err?.error?.detail ?? 'Could not upload the image.'),
    });
  }

  adjustStock(product: Product, variantId: number, delta: number): void {
    this.api.adjustStock(product.id, variantId, delta).subscribe({ next: () => this.load() });
  }

  deleteProduct(product: Product): void {
    if (!confirm(`Delete "${product.name}"? It is removed permanently unless it was already sold, in which case it is kept and deactivated so order history stays intact.`)) {
      return;
    }
    this.api.deleteProduct(product.id).subscribe({
      next: (result) => {
        this.productMessage.set(result.deleted
          ? `"${product.name}" deleted.`
          : `"${product.name}" was already sold, so it was deactivated instead of deleted.`);
        if (this.editingProductId === product.id) {
          this.productFormOpen.set(false);
          this.resetProduct();
        }
        this.load();
      },
      error: (err) => this.productMessage.set(err?.error?.detail ?? 'Could not delete the product.'),
    });
  }

  // ---- Helpers -----------------------------------------------------------

  private blankVariant(): VariantDraft {
    return { sizeLabel: 'M', sku: '', stockQuantity: 5, lowStockThreshold: 2, active: true };
  }

  private firstCategoryForGender(genderId: number | null): number | null {
    if (!genderId) return this.categories()[0]?.id ?? null;
    const options = this.categories().filter((category) => this.isInGender(category, genderId));
    const leaf = options.find((category) => !options.some((child) => child.parentId === category.id));
    return leaf?.id ?? options[0]?.id ?? null;
  }

  private isInGender(category: Category, genderId: number): boolean {
    let current: Category | undefined = category;
    while (current) {
      if (current.id === genderId) return true;
      current = current.parentId == null
        ? undefined
        : this.categories().find((item) => item.id === current!.parentId);
    }
    return false;
  }

  private rootOf(categoryId: number | null): Category | null {
    if (categoryId == null) return null;
    let current = this.categories().find((category) => category.id === categoryId) ?? null;
    while (current?.parentId != null) {
      current = this.categories().find((category) => category.id === current!.parentId) ?? null;
    }
    return current;
  }

  private flatten(categories: Category[]): Category[] {
    const result: Category[] = [];
    const walk = (items: Category[]) => {
      for (const item of items) {
        result.push(item);
        if (item.children?.length) walk(item.children);
      }
    };
    walk(categories);
    return result;
  }

  private scrollTo(id: string): void {
    if (typeof document === 'undefined') return;
    // The form panel may be rendered for the first time on this click, so wait
    // for Angular to attach it before scrolling.
    setTimeout(() => {
      document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 0);
  }
}
