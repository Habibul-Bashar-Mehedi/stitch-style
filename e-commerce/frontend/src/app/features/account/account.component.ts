import { DatePipe, NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MyOrder, ReturnRecord } from '../../core/models';
import { formatBdt } from '../../core/format';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-account',
  imports: [DatePipe, NgClass, RouterLink, TranslatePipe],
  templateUrl: './account.component.html',
})
export class AccountComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly orders = signal<MyOrder[]>([]);
  readonly returns = signal<ReturnRecord[]>([]);
  readonly tab = signal<'orders' | 'returns'>('orders');
  readonly loading = signal(true);
  readonly orderCount = computed(() => this.orders().length);
  readonly returnCount = computed(() => this.returns().length);
  readonly formatBdt = formatBdt;

  private readonly returnStatusKeys: Record<string, string> = {
    REQUESTED: 'return.status.requested',
    APPROVED: 'return.status.approved',
    REJECTED: 'return.status.rejected',
    RECEIVED: 'return.status.received',
    REFUNDED: 'return.status.refunded',
  };
  private readonly returnBadges: Record<string, string> = {
    REQUESTED: 'badge-warning',
    APPROVED: 'badge-info',
    REJECTED: 'badge-error',
    RECEIVED: 'badge-primary',
    REFUNDED: 'badge-success',
  };
  private readonly orderBadges: Record<string, string> = {
    PENDING: 'badge-warning',
    SHIPPED: 'badge-info',
    DELIVERED: 'badge-success',
    CANCELLED: 'badge-error',
  };
  private readonly paymentBadges: Record<string, string> = {
    UNPAID: 'badge-warning',
    INITIATED: 'badge-info',
    PAID: 'badge-success',
    FAILED: 'badge-error',
    EXPIRED: 'badge-ghost',
    REFUNDED: 'badge-neutral',
    PARTIALLY_REFUNDED: 'badge-warning',
  };

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      this.tab.set(params.get('tab') === 'returns' ? 'returns' : 'orders');
    });
    if (this.auth.me()) {
      this.load();
    } else {
      this.auth.load().subscribe(() => this.load());
    }
  }

  setTab(tab: 'orders' | 'returns'): void {
    this.tab.set(tab);
    this.router.navigate([], {
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  statusKey(status: string): string {
    return this.returnStatusKeys[status] ?? 'return.status.requested';
  }

  returnBadge(status: string): string {
    return this.returnBadges[status] ?? 'badge-ghost';
  }

  orderBadge(status: string): string {
    return this.orderBadges[status] ?? 'badge-ghost';
  }

  paymentBadge(status: string): string {
    return this.paymentBadges[status] ?? 'badge-ghost';
  }

  private load(): void {
    if (!this.auth.me()) {
      this.loading.set(false);
      return;
    }
    forkJoin({
      orders: this.api.getMyOrders(),
      returns: this.api.getMyReturns(),
    }).subscribe({
      next: ({ orders, returns }) => {
        this.orders.set(orders);
        this.returns.set(returns);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
