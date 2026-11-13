import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the CSRF token and the home payload with a language', () => {
    api.getCsrf().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/csrf' }).flush(null);

    api.getHome('bn').subscribe();
    const home = http.expectOne((request) => request.url === '/api/v1/public/home');
    expect(home.request.params.get('lang')).toBe('bn');
    home.flush({ settings: {}, banners: [], notices: [], cards: [], latestProducts: [] });
  });

  it('builds category and product URLs', () => {
    api.getCategories().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/categories/tree' }).flush([]);

    api.getCategory('men').subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/categories/men' }).flush({});

    api.getProduct('panjabi').subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/products/panjabi' }).flush({});

    api.relatedProducts('panjabi').subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/products/panjabi/related' }).flush([]);
  });

  it('drops empty and undefined filter parameters', () => {
    api.getProducts({ q: 'panjabi', page: 0, empty: '', missing: undefined }).subscribe();
    const request = http.expectOne((item) => item.url === '/api/v1/public/products');
    expect(request.request.params.get('q')).toBe('panjabi');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.has('empty')).toBe(false);
    expect(request.request.params.has('missing')).toBe(false);
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('prices and mutates the cart', () => {
    api.priceCart([{ variantId: 1, quantity: 2, selected: true }]).subscribe();
    const price = http.expectOne({ method: 'POST', url: '/api/v1/public/cart/price' });
    expect(price.request.body).toEqual({ items: [{ variantId: 1, quantity: 2, selected: true }] });
    price.flush({ lines: [], subtotal: 0, deliveryCharge: 0, discountTotal: 0, grandTotal: 0 });

    api.replaceCart([]).subscribe();
    http.expectOne({ method: 'PUT', url: '/api/v1/me/cart' }).flush({});

    api.removeCartLine(9).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/v1/me/cart/9' }).flush({});

    api.mergeCart([]).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/me/cart/merge' }).flush({});
  });

  it('sends the Idempotency-Key header when placing an order', () => {
    const payload = {
      customer: { name: 'Nusrat', phone: '01712345678' },
      shippingAddress: 'Dhaka',
      deliveryAreaId: 1,
      paymentMethod: 'COD' as const,
      items: [{ variantId: 1, quantity: 1, selected: true }],
    };

    api.placeOrder(payload, 'key-123').subscribe();
    const request = http.expectOne({ method: 'POST', url: '/api/v1/public/orders' });
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-123');
    expect(request.request.body).toEqual(payload);
    request.flush({});
  });

  it('supports order tracking by token and by phone', () => {
    api.track('tok').subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/orders/track/tok' }).flush({});

    api.trackByPhone('ORD-1', '01712345678').subscribe();
    const request = http.expectOne({ method: 'POST', url: '/api/v1/public/orders/track' });
    expect(request.request.body).toEqual({ orderNumber: 'ORD-1', phone: '01712345678' });
    request.flush({});
  });

  it('covers account, admin and catalogue mutation endpoints', () => {
    api.getMe().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/me' }).flush({});
    api.getMyOrders().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/me/orders' }).flush([]);
    api.getMyReturns().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/me/returns' }).flush([]);

    api.adminSession().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/session' }).flush({});
    api.adminCategories().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/categories' }).flush([]);
    api.createCategory({ name: 'Men' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/categories' }).flush({});
    api.setCategoryVisibility(1, false).subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/categories/1/visibility' }).flush(null);
    api.reorderCategories(null, [2, 1]).subscribe();
    http.expectOne({ method: 'PUT', url: '/api/v1/admin/categories/reorder' }).flush(null);
    api.deleteCategory(1).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/v1/admin/categories/1' }).flush(null);

    api.createProduct({ name: 'Panjabi' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/products' }).flush({});
    api.adjustStock(1, 2, 5).subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/products/1/variants/2/stock' }).flush({});
    api.deleteProduct(1).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/v1/admin/products/1' }).flush({});

    api.adminOrders(1, 10, { status: 'PENDING', phone: '', q: undefined }).subscribe();
    const orders = http.expectOne((request) => request.url === '/api/v1/admin/orders');
    expect(orders.request.params.get('page')).toBe('1');
    expect(orders.request.params.get('status')).toBe('PENDING');
    expect(orders.request.params.has('phone')).toBe(false);
    orders.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });

    api.updateOrderStatus(1, 'SHIPPED', 'reason').subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/orders/1/status' }).flush({});
    api.recordCourier(1, 'Pathao', 'TRIP').subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/orders/1/courier' }).flush(null);
    api.createShipment(1, 'PATHAO').subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/orders/1/shipment' }).flush(null);
    api.adminInvoices([1, 2]).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/orders/invoices' }).flush([]);

    api.adminStaff().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/staff' }).flush([]);
    api.createStaff({ email: 'a@b.c' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/staff' }).flush({});
    api.setStaffActive(1, false).subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/staff/1/active' }).flush({});

    api.adminReturns().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/returns' }).flush([]);
    api.advanceReturn(1, 'APPROVED').subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/returns/1' }).flush({});
    api.refundReturn(1, 'MANUAL', 10).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/returns/1/refund' }).flush({});

    api.adminComplaints().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/complaints' }).flush([]);
    api.respondComplaint(1, 'Done', true).subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/v1/admin/complaints/1' }).flush({});

    api.adminCoupons().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/coupons' }).flush([]);
    api.validateCoupon('SAVE', 100).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/public/coupons/validate' }).flush({});

    api.adminReviews().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/reviews' }).flush([]);
    api.productReviews('panjabi').subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/public/products/panjabi/reviews' }).flush([]);
    api.submitReview('panjabi', 5, 'Great').subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/me/products/panjabi/reviews' }).flush({});

    api.adminAnalytics().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/analytics/summary' }).flush({});
    api.adminSettings().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/settings' }).flush([]);
    api.updateSettings([{ key: 'brand.name', value: 'Stitch & Style' }]).subscribe();
    http.expectOne({ method: 'PUT', url: '/api/v1/admin/settings' }).flush([]);

    api.adminDeliveryAreas().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/delivery-areas' }).flush([]);
    api.createDeliveryArea({ name: 'Dhaka' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/delivery-areas' }).flush({});
    api.deleteDeliveryArea(1).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/v1/admin/delivery-areas/1' }).flush(null);

    api.adminBanners().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/banners' }).flush([]);
    api.deleteBanner(1).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/v1/admin/banners/1' }).flush(null);
    api.adminNotices().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/notices' }).flush([]);
    api.deleteNotice(1).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/v1/admin/notices/1' }).flush(null);
  });

  it('uploads media as multipart form data', () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    api.uploadMedia(file).subscribe();

    const request = http.expectOne({ method: 'POST', url: '/api/v1/admin/media' });
    expect(request.request.body instanceof FormData).toBe(true);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush({ id: 1, url: '/media/a.png' });
  });
});
