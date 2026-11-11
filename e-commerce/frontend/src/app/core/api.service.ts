import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from './api-base';
import {
  AdminOrder,
  Analytics,
  Banner,
  AuditLog,
  Cart,
  CartInput,
  Category,
  CheckoutPayload,
  Complaint,
  Coupon,
  DeliveryArea,
  Home,
  Invoice,
  Me,
  MyOrder,
  Notice,
  OrderResponse,
  PageResponse,
  PaymentMethod,
  Product,
  ReturnQuote,
  ReturnRecord,
  Review,
  Staff,
  Tracking,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  getCsrf() {
    return this.http.get<void>(this.url('/api/v1/public/csrf'));
  }

  getHome(lang = 'en') {
    return this.http.get<Home>(this.url('/api/v1/public/home'), { params: { lang } });
  }

  getCategories() {
    return this.http.get<Category[]>(this.url('/api/v1/public/categories/tree'));
  }

  getCategory(slug: string) {
    return this.http.get<Category>(this.url(`/api/v1/public/categories/${slug}`));
  }

  getCategoryProducts(slug: string, params: Record<string, string | number | undefined>) {
    return this.http.get<PageResponse<Product>>(this.url(`/api/v1/public/categories/${slug}/products`), {
      params: this.params(params),
    });
  }

  getProducts(params: Record<string, string | number | undefined>) {
    return this.http.get<PageResponse<Product>>(this.url('/api/v1/public/products'), {
      params: this.params(params),
    });
  }

  getProduct(slug: string) {
    return this.http.get<Product>(this.url(`/api/v1/public/products/${slug}`));
  }

  getDeliveryAreas() {
    return this.http.get<DeliveryArea[]>(this.url('/api/v1/public/delivery-areas'));
  }

  getPaymentMethods() {
    return this.http.get<PaymentMethod[]>(this.url('/api/v1/public/payment-methods'));
  }

  priceCart(items: CartInput[]) {
    return this.http.post<Cart>(this.url('/api/v1/public/cart/price'), { items });
  }

  getCart() {
    return this.http.get<Cart>(this.url('/api/v1/me/cart'));
  }

  replaceCart(items: CartInput[]) {
    return this.http.put<Cart>(this.url('/api/v1/me/cart'), { items });
  }

  removeCartLine(variantId: number) {
    return this.http.delete<Cart>(this.url(`/api/v1/me/cart/${variantId}`));
  }

  mergeCart(items: CartInput[]) {
    return this.http.post<Cart>(this.url('/api/v1/me/cart/merge'), { items });
  }

  placeOrder(payload: CheckoutPayload, idempotencyKey: string) {
    return this.http.post<OrderResponse>(this.url('/api/v1/public/orders'), payload, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  track(token: string) {
    return this.http.get<Tracking>(this.url(`/api/v1/public/orders/track/${token}`));
  }

  trackByPhone(orderNumber: string, phone: string) {
    return this.http.post<Tracking>(this.url('/api/v1/public/orders/track'), { orderNumber, phone });
  }

  getMe() {
    return this.http.get<Me>(this.url('/api/v1/me'));
  }

  logout() {
    return this.http.post<void>(this.url('/api/v1/auth/logout'), {});
  }

  getMyOrders() {
    return this.http.get<MyOrder[]>(this.url('/api/v1/me/orders'));
  }

  getMyOrder(orderNumber: string) {
    return this.http.get<Tracking>(this.url(`/api/v1/me/orders/${orderNumber}`));
  }

  getMyReturns() {
    return this.http.get<ReturnRecord[]>(this.url('/api/v1/me/returns'));
  }

  getReturnQuote(orderNumber: string, items: { orderItemId: number; quantity: number }[]) {
    return this.http.post<ReturnQuote>(this.url(`/api/v1/me/orders/${orderNumber}/return-quote`), { items });
  }

  adminSession() {
    return this.http.get<Me>(this.url('/api/v1/admin/session'));
  }

  adminCategories() {
    return this.http.get<Category[]>(this.url('/api/v1/admin/categories'));
  }

  createCategory(payload: unknown) {
    return this.http.post<Category>(this.url('/api/v1/admin/categories'), payload);
  }

  updateCategory(id: number, payload: unknown) {
    return this.http.put<Category>(this.url(`/api/v1/admin/categories/${id}`), payload);
  }

  setCategoryVisibility(id: number, active: boolean) {
    return this.http.patch<void>(this.url(`/api/v1/admin/categories/${id}/visibility`), { active });
  }

  reorderCategories(parentId: number | null, orderedIds: number[]) {
    return this.http.put<void>(this.url('/api/v1/admin/categories/reorder'), { parentId, orderedIds });
  }

  deleteCategory(id: number) {
    return this.http.delete<void>(this.url(`/api/v1/admin/categories/${id}`));
  }

  adminProducts(page = 0, size = 20) {
    return this.http.get<PageResponse<Product>>(this.url('/api/v1/admin/products'), { params: { page, size } });
  }

  createProduct(payload: unknown) {
    return this.http.post<Product>(this.url('/api/v1/admin/products'), payload);
  }

  updateProduct(id: number, payload: unknown) {
    return this.http.put<Product>(this.url(`/api/v1/admin/products/${id}`), payload);
  }

  deleteProduct(id: number) {
    return this.http.delete<{ deleted: boolean; deactivated: boolean }>(this.url(`/api/v1/admin/products/${id}`));
  }

  adjustStock(productId: number, variantId: number, delta: number) {
    return this.http.patch(this.url(`/api/v1/admin/products/${productId}/variants/${variantId}/stock`), { delta });
  }

  adminOrders(page = 0, size = 20, filters: Record<string, string | number | undefined> = {}) {
    return this.http.get<PageResponse<AdminOrder>>(this.url('/api/v1/admin/orders'), {
      params: this.params({ page, size, ...filters }),
    });
  }

  adminOrder(id: number) {
    return this.http.get<Tracking>(this.url(`/api/v1/admin/orders/${id}`));
  }

  updateOrderStatus(id: number, status: string, reason?: string) {
    return this.http.patch(this.url(`/api/v1/admin/orders/${id}/status`), { status, reason });
  }

  recordCourier(id: number, courierName: string, trackingNumber: string) {
    return this.http.patch(this.url(`/api/v1/admin/orders/${id}/courier`), { courierName, trackingNumber });
  }

  createShipment(id: number, courierCode: string) {
    return this.http.post(this.url(`/api/v1/admin/orders/${id}/shipment`), { courierCode });
  }

  adminInvoices(orderIds: number[]) {
    return this.http.post<Invoice[]>(this.url('/api/v1/admin/orders/invoices'), { orderIds });
  }

  adminStaff() {
    return this.http.get<Staff[]>(this.url('/api/v1/admin/staff'));
  }

  createStaff(payload: unknown) {
    return this.http.post<Staff>(this.url('/api/v1/admin/staff'), payload);
  }

  updateStaff(id: number, payload: unknown) {
    return this.http.put<Staff>(this.url(`/api/v1/admin/staff/${id}`), payload);
  }

  setStaffActive(id: number, active: boolean) {
    return this.http.patch<Staff>(this.url(`/api/v1/admin/staff/${id}/active`), { active });
  }

  adminAudit(params: Record<string, string | number | undefined> = {}) {
    return this.http.get<PageResponse<AuditLog>>(this.url('/api/v1/admin/audit'), { params: this.params(params) });
  }

  adminReturns() {
    return this.http.get<ReturnRecord[]>(this.url('/api/v1/admin/returns'));
  }

  advanceReturn(id: number, status: string, note?: string) {
    return this.http.patch<ReturnRecord>(this.url(`/api/v1/admin/returns/${id}`), { status, note });
  }

  refundReturn(id: number, method: string, amount?: number) {
    return this.http.post<ReturnRecord>(this.url(`/api/v1/admin/returns/${id}/refund`), { method, amount });
  }

  requestReturn(orderNumber: string, payload: unknown) {
    return this.http.post<ReturnRecord>(this.url(`/api/v1/me/orders/${orderNumber}/returns`), payload);
  }

  adminComplaints() {
    return this.http.get<Complaint[]>(this.url('/api/v1/admin/complaints'));
  }

  respondComplaint(id: number, response: string, close = false) {
    return this.http.patch<Complaint>(this.url(`/api/v1/admin/complaints/${id}`), { response, close });
  }

  submitComplaint(payload: unknown) {
    return this.http.post<Complaint>(this.url('/api/v1/public/complaints'), payload);
  }

  adminCoupons() {
    return this.http.get<Coupon[]>(this.url('/api/v1/admin/coupons'));
  }

  createCoupon(payload: unknown) {
    return this.http.post<Coupon>(this.url('/api/v1/admin/coupons'), payload);
  }

  updateCoupon(id: number, payload: unknown) {
    return this.http.put<Coupon>(this.url(`/api/v1/admin/coupons/${id}`), payload);
  }

  deleteCoupon(id: number) {
    return this.http.delete<void>(this.url(`/api/v1/admin/coupons/${id}`));
  }

  validateCoupon(code: string, subtotal: number) {
    return this.http.post<{ code: string; discount: number; message: string }>(this.url('/api/v1/public/coupons/validate'), { code, subtotal });
  }

  adminReviews() {
    return this.http.get<Review[]>(this.url('/api/v1/admin/reviews'));
  }

  moderateReview(id: number, status: string) {
    return this.http.patch<Review>(this.url(`/api/v1/admin/reviews/${id}`), { status });
  }

  productReviews(slug: string) {
    return this.http.get<Review[]>(this.url(`/api/v1/public/products/${slug}/reviews`));
  }

  submitReview(slug: string, rating: number, comment: string) {
    return this.http.post<Review>(this.url(`/api/v1/me/products/${slug}/reviews`), { rating, comment });
  }

  adminAnalytics(from?: string, to?: string) {
    return this.http.get<Analytics>(this.url('/api/v1/admin/analytics/summary'), { params: this.params({ from, to }) });
  }

  adminSettings() {
    return this.http.get<{ key: string; value: string; valueType: string }[]>(this.url('/api/v1/admin/settings'));
  }

  updateSettings(settings: { key: string; value: string; valueType?: string }[]) {
    return this.http.put<{ key: string; value: string; valueType: string }[]>(this.url('/api/v1/admin/settings'), settings);
  }

  adminDeliveryAreas() {
    return this.http.get<DeliveryArea[]>(this.url('/api/v1/admin/delivery-areas'));
  }

  createDeliveryArea(payload: unknown) {
    return this.http.post<DeliveryArea>(this.url('/api/v1/admin/delivery-areas'), payload);
  }

  updateDeliveryArea(id: number, payload: unknown) {
    return this.http.put<DeliveryArea>(this.url(`/api/v1/admin/delivery-areas/${id}`), payload);
  }

  deleteDeliveryArea(id: number) {
    return this.http.delete<void>(this.url(`/api/v1/admin/delivery-areas/${id}`));
  }

  adminBanners() {
    return this.http.get<Banner[]>(this.url('/api/v1/admin/banners'));
  }

  createBanner(payload: unknown) {
    return this.http.post<Banner>(this.url('/api/v1/admin/banners'), payload);
  }

  deleteBanner(id: number) {
    return this.http.delete<void>(this.url(`/api/v1/admin/banners/${id}`));
  }

  adminNotices() {
    return this.http.get<Notice[]>(this.url('/api/v1/admin/notices'));
  }

  createNotice(payload: unknown) {
    return this.http.post<Notice>(this.url('/api/v1/admin/notices'), payload);
  }

  deleteNotice(id: number) {
    return this.http.delete<void>(this.url(`/api/v1/admin/notices/${id}`));
  }

  uploadMedia(file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ id: number; url: string }>(this.url('/api/v1/admin/media'), form);
  }

  relatedProducts(slug: string) {
    return this.http.get<Product[]>(this.url(`/api/v1/public/products/${slug}/related`));
  }

  private url(path: string): string {
    return `${this.base}${path}`;
  }

  private params(values: Record<string, string | number | undefined>): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(values)) {
      if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    }
    return params;
  }
}
