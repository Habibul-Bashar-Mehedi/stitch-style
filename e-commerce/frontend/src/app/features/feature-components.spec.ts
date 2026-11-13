import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApiService } from '../core/api.service';
import { CartService } from '../core/cart.service';
import { formatBdt } from '../core/format';
import { Home } from '../core/models';
import { WishlistService } from '../core/wishlist.service';
import { CartComponent } from './cart/cart.component';
import { ComplaintComponent } from './complaints/complaint.component';
import { HomeComponent } from './home/home.component';
import { LegalComponent } from './legal/legal.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { WishlistComponent } from './wishlist/wishlist.component';

const home: Home = {
  settings: { 'brand.name': 'Stitch & Style' },
  banners: [{ id: 1, title: 'Eid Collection', imageUrl: '/hero.jpg', displayOrder: 0 }],
  notices: [],
  cards: [{
    id: 1, parentId: null, name: 'Men', slug: 'men', sizeType: 'LETTER', displayOrder: 0,
    depth: 0, active: true, showOnHome: true, showInNavbar: true, children: [],
  }],
  latestProducts: [],
};

describe('HomeComponent', () => {
  it('renders the hero banner and collection cards from the API', () => {
    const api = { getHome: vi.fn(() => of(home)) };
    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(api.getHome).toHaveBeenCalled();
    expect(fixture.componentInstance.hero()?.title).toBe('Eid Collection');
    expect(fixture.componentInstance.productLink({ slug: 'panjabi' } as never)).toBe('/products/panjabi');
    expect(fixture.nativeElement.textContent).toContain('Eid Collection');
    expect(fixture.nativeElement.textContent).toContain('Men');
    expect(TestBed.inject(Title).getTitle()).toContain('Stitch & Style');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).toBeTruthy();
  });

  it('tolerates a failed home request', () => {
    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: { getHome: vi.fn(() => throwError(() => new Error('offline'))) } }],
    });
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.home()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Stitch & Style');
  });
});

describe('CartComponent', () => {
  it('renders lines and delegates quantity, selection and removal actions', () => {
    const cart = {
      cart: signal({
        lines: [{
          variantId: 1, productId: 2, productSlug: 'panjabi', productName: 'Panjabi', sizeLabel: 'M',
          quantity: 2, selected: true, unitPrice: 500, lineTotal: 1000, available: 5,
          priceChanged: false, stockChanged: false,
        }],
        subtotal: 1000, deliveryCharge: 0, discountTotal: 0, grandTotal: 1000,
      }),
      selectedCount: signal(1),
      toggleAll: vi.fn(),
      updateQuantity: vi.fn(),
      remove: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [provideRouter([]), { provide: CartService, useValue: cart }],
    });
    const fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.lines().length).toBe(1);
    expect(fixture.componentInstance.allSelected()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Panjabi');
    expect(fixture.nativeElement.textContent).toContain(formatBdt(1000));

    fixture.componentInstance.toggleAll({ target: { checked: false } } as unknown as Event);
    expect(cart.toggleAll).toHaveBeenCalledWith(false);
    fixture.componentInstance.changeQuantity(1, 3);
    expect(cart.updateQuantity).toHaveBeenCalledWith(1, 3);
    fixture.componentInstance.remove(1);
    expect(cart.remove).toHaveBeenCalledWith(1);
  });
});

describe('WishlistComponent', () => {
  beforeEach(() => localStorage.clear());

  it('shows the empty state and then the saved items', () => {
    TestBed.configureTestingModule({
      imports: [WishlistComponent],
      providers: [provideRouter([])],
    });
    const fixture = TestBed.createComponent(WishlistComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nothing saved yet');

    TestBed.inject(WishlistService).toggle({
      productId: 1, slug: 'panjabi', name: 'Panjabi', price: 1200, imageUrl: '/p.png',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Panjabi');
    expect(fixture.nativeElement.textContent).toContain(formatBdt(1200));
  });
});

describe('ComplaintComponent', () => {
  it('ignores incomplete forms, submits valid ones and reports success', () => {
    const api = { submitComplaint: vi.fn(() => of({ id: 1 })) };
    TestBed.configureTestingModule({
      imports: [ComplaintComponent],
      providers: [{ provide: ApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(ComplaintComponent);
    const component = fixture.componentInstance;

    component.submit();
    expect(api.submitComplaint).not.toHaveBeenCalled();

    component.name = ' Nusrat ';
    component.phone = ' 01712345678 ';
    component.message = ' Help ';
    component.orderNumber = ' ';
    component.submit();

    expect(api.submitComplaint).toHaveBeenCalledWith({
      name: 'Nusrat', phone: '01712345678', orderNumber: undefined, message: 'Help',
    });
    expect(component.submitted()).toBe(true);
  });

  it('surfaces the server problem detail on failure', () => {
    const api = {
      submitComplaint: vi.fn(() => throwError(() => ({ error: { detail: 'Rate limited' } }))),
    };
    TestBed.configureTestingModule({
      imports: [ComplaintComponent],
      providers: [{ provide: ApiService, useValue: api }],
    });
    const component = TestBed.createComponent(ComplaintComponent).componentInstance;
    component.name = 'N';
    component.phone = '017';
    component.message = 'M';
    component.submit();
    expect(component.error()).toBe('Rate limited');
  });
});

describe('LegalComponent', () => {
  it('reads the title and slug from the route', () => {
    const route = {
      snapshot: {
        data: { title: 'Terms & conditions' },
        routeConfig: { path: 'terms' },
        paramMap: convertToParamMap({}),
      },
    } as unknown as ActivatedRoute;
    TestBed.configureTestingModule({
      imports: [LegalComponent],
      providers: [{ provide: ActivatedRoute, useValue: route }],
    });
    const component = TestBed.createComponent(LegalComponent).componentInstance;
    expect(component.title).toBe('Terms & conditions');
    expect(component.slug).toBe('terms');
  });
});

describe('NotFoundComponent', () => {
  it('renders a 404 message with a link home', () => {
    TestBed.configureTestingModule({ imports: [NotFoundComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('404');
    expect(fixture.nativeElement.textContent).toContain('Page not found');
  });
});
