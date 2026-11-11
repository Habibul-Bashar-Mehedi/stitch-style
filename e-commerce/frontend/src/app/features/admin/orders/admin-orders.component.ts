import { DatePipe, NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AdminOrder } from '../../../core/models';
import { formatBdt } from '../../../core/format';

@Component({
  selector: 'app-admin-orders',
  imports: [FormsModule, RouterLink, DatePipe, NgClass],
  templateUrl: './admin-orders.component.html',
})
export class AdminOrdersComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly orders = signal<AdminOrder[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalOrders = signal(0);
  readonly selected = signal<Set<number>>(new Set());
  readonly expanded = signal<Set<number>>(new Set());
  readonly message = signal<string | null>(null);
  readonly formatBdt = formatBdt;

  readonly selectedCount = computed(() => this.selected().size);
  readonly allSelected = computed(() => this.orders().length > 0 && this.selected().size === this.orders().length);

  statusFilter = '';
  phoneFilter = '';
  numberFilter = '';
  nextStatus: Record<number, string> = {};
  courierName: Record<number, string> = {};
  courierTracking: Record<number, string> = {};
  courierCode = 'PATHAO';

  /** Mirrors OrderService.allowed(...) so the UI can only offer real transitions. */
  private readonly nextStatusOptions: Record<string, string[]> = {
    PENDING: ['SHIPPED', 'CANCELLED'],
    SHIPPED: ['DELIVERED', 'CANCELLED'],
    DELIVERED: [],
    CANCELLED: [],
  };

  private readonly statusBadges: Record<string, string> = {
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
    this.load();
  }

  load(): void {
    this.api.adminOrders(this.page(), 20, {
      status: this.statusFilter || undefined,
      phone: this.phoneFilter || undefined,
      orderNumber: this.numberFilter || undefined,
    }).subscribe({
      next: (page) => {
        this.orders.set(page.content);
        this.totalPages.set(page.totalPages);
        this.totalOrders.set(page.totalElements ?? page.content.length);
        this.nextStatus = Object.fromEntries(
          page.content.map((order) => [order.id, this.statusOptions(order)[0] ?? order.status]),
        );
      },
      error: () => this.message.set('Could not load orders.'),
    });
  }

  statusOptions(order: AdminOrder): string[] {
    return this.nextStatusOptions[order.status] ?? [];
  }

  statusBadge(status: string): string {
    return this.statusBadges[status] ?? 'badge-ghost';
  }

  paymentBadge(status: string): string {
    return this.paymentBadges[status] ?? 'badge-ghost';
  }

  canCancel(order: AdminOrder): boolean {
    return this.statusOptions(order).includes('CANCELLED');
  }

  toggleExpanded(id: number): void {
    const next = new Set(this.expanded());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.expanded.set(next);
  }

  toggle(id: number): void {
    const next = new Set(this.selected());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selected.set(next);
  }

  toggleAll(): void {
    const all = this.orders().map((order) => order.id);
    this.selected.set(this.selected().size === all.length ? new Set() : new Set(all));
  }

  printInvoices(): void {
    const ids = [...this.selected()];
    if (!ids.length) {
      this.message.set('Select at least one order to print.');
      return;
    }
    this.router.navigate(['/admin/invoices'], { queryParams: { ids: ids.join(',') } });
  }

  transition(order: AdminOrder): void {
    const status = this.nextStatus[order.id];
    if (!status) return;
    const call = status === 'CANCELLED'
      ? this.api.updateOrderStatus(order.id, status, 'Cancelled by admin')
      : this.api.updateOrderStatus(order.id, status);
    call.subscribe({
      next: () => {
        this.message.set(status === 'CANCELLED'
          ? `Order ${order.orderNumber} cancelled.`
          : `Order ${order.orderNumber} moved to ${status}.`);
        this.load();
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Status transition failed.'),
    });
  }

  cancel(order: AdminOrder): void {
    if (!this.canCancel(order)) {
      this.message.set(`Order ${order.orderNumber} is ${order.status} and can no longer be cancelled.`);
      return;
    }
    const reason = window.prompt(`Cancel order ${order.orderNumber}? Optional reason:`, 'Cancelled by admin');
    if (reason === null) return;
    this.api.updateOrderStatus(order.id, 'CANCELLED', reason.trim() || 'Cancelled by admin').subscribe({
      next: () => {
        this.message.set(`Order ${order.orderNumber} cancelled.`);
        this.load();
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not cancel the order.'),
    });
  }

  createShipment(order: AdminOrder): void {
    this.api.createShipment(order.id, this.courierCode).subscribe({
      next: () => { this.message.set('Shipment created.'); this.load(); },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not create the shipment.'),
    });
  }

  saveCourier(order: AdminOrder): void {
    this.api.recordCourier(order.id, this.courierName[order.id] ?? '', this.courierTracking[order.id] ?? '').subscribe({
      next: () => this.message.set('Courier saved.'),
      error: () => this.message.set('Could not save courier.'),
    });
  }

  pageChange(delta: number): void {
    this.page.set(Math.max(0, this.page() + delta));
    this.load();
  }
}
