import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink],
  template: `
    <div class="border-b border-base-300 bg-base-200">
      <div class="page-container flex flex-wrap items-center gap-2 py-3">
        <a class="btn btn-ghost btn-sm font-black" routerLink="/admin">Admin</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/catalog">Products &amp; collections</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/orders">Orders</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/messages">Messages</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/returns">Returns</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/complaints">Complaints</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/coupons">Coupons</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/reviews">Reviews</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/staff">Staff</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/audit">Audit</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/settings">Settings</a>
        <a class="btn btn-ghost btn-sm" routerLink="/admin/banners">Banners</a>
        <span class="ml-auto text-xs opacity-60">{{ auth.me()?.role }}</span>
      </div>
    </div>
    <router-outlet />
  `,
})
export class AdminLayoutComponent {
  readonly auth = inject(AuthService);
}
