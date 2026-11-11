import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { staffGuard } from './core/guards';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'collections/:slug', loadComponent: () => import('./features/collections/collection.component').then((m) => m.CollectionComponent) },
  { path: 'products/:slug', loadComponent: () => import('./features/products/product.component').then((m) => m.ProductComponent) },
  { path: 'cart', loadComponent: () => import('./features/cart/cart.component').then((m) => m.CartComponent) },
  { path: 'checkout', loadComponent: () => import('./features/checkout/checkout.component').then((m) => m.CheckoutComponent) },
  { path: 'track', loadComponent: () => import('./features/tracking/tracking.component').then((m) => m.TrackingComponent) },
  { path: 'account', loadComponent: () => import('./features/account/account.component').then((m) => m.AccountComponent) },
  { path: 'messages', loadComponent: () => import('./features/chat/messages.component').then((m) => m.MessagesComponent) },
  { path: 'wishlist', loadComponent: () => import('./features/wishlist/wishlist.component').then((m) => m.WishlistComponent) },
  { path: 'contact', loadComponent: () => import('./features/complaints/complaint.component').then((m) => m.ComplaintComponent) },
  { path: 'returns/request', loadComponent: () => import('./features/returns-request/return-request.component').then((m) => m.ReturnRequestComponent) },
  {
    path: 'admin',
    loadComponent: () => import('./features/admin/admin-layout.component').then((m) => m.AdminLayoutComponent),
    canActivate: [staffGuard],
    children: [
      { path: '', loadComponent: () => import('./features/admin/dashboard/admin-dashboard.component').then((m) => m.AdminDashboardComponent) },
      { path: 'catalog', loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent) },
      { path: 'orders', loadComponent: () => import('./features/admin/orders/admin-orders.component').then((m) => m.AdminOrdersComponent) },
      { path: 'messages', loadComponent: () => import('./features/admin/messages/admin-messages.component').then((m) => m.AdminMessagesComponent) },
      { path: 'invoices', loadComponent: () => import('./features/admin/invoices/admin-invoices.component').then((m) => m.AdminInvoicesComponent) },
      { path: 'returns', loadComponent: () => import('./features/admin/returns/admin-returns.component').then((m) => m.AdminReturnsComponent) },
      { path: 'complaints', loadComponent: () => import('./features/admin/complaints/admin-complaints.component').then((m) => m.AdminComplaintsComponent) },
      { path: 'coupons', loadComponent: () => import('./features/admin/coupons/admin-coupons.component').then((m) => m.AdminCouponsComponent) },
      { path: 'reviews', loadComponent: () => import('./features/admin/reviews/admin-reviews.component').then((m) => m.AdminReviewsComponent) },
      { path: 'staff', loadComponent: () => import('./features/admin/staff/admin-staff.component').then((m) => m.AdminStaffComponent) },
      { path: 'audit', loadComponent: () => import('./features/admin/audit/admin-audit.component').then((m) => m.AdminAuditComponent) },
      { path: 'settings', loadComponent: () => import('./features/admin/settings/admin-settings.component').then((m) => m.AdminSettingsComponent) },
      { path: 'banners', loadComponent: () => import('./features/admin/banners/admin-banners.component').then((m) => m.AdminBannersComponent) },
    ],
  },
  { path: 'terms', loadComponent: () => import('./features/legal/legal.component').then((m) => m.LegalComponent), data: { title: 'Terms & conditions' } },
  { path: 'privacy', loadComponent: () => import('./features/legal/legal.component').then((m) => m.LegalComponent), data: { title: 'Privacy notice' } },
  { path: 'returns', loadComponent: () => import('./features/legal/legal.component').then((m) => m.LegalComponent), data: { title: 'Returns & refunds' } },
  { path: 'delivery', loadComponent: () => import('./features/legal/legal.component').then((m) => m.LegalComponent), data: { title: 'Delivery terms' } },
  { path: '**', loadComponent: () => import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent) },
];
