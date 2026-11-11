import { DatePipe, NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Analytics } from '../../../core/models';
import { formatBdt } from '../../../core/format';

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, DatePipe, NgClass],
  templateUrl: './admin-dashboard.component.html',
})
export class AdminDashboardComponent {
  private readonly api = inject(ApiService);
  readonly analytics = signal<Analytics | null>(null);
  readonly formatBdt = formatBdt;

  readonly maxStatusCount = computed(() =>
    Math.max(1, ...(this.analytics()?.statusDistribution.map((status) => status.count) ?? [0])),
  );

  private readonly statusBadges: Record<string, string> = {
    PENDING: 'badge-warning',
    SHIPPED: 'badge-info',
    DELIVERED: 'badge-success',
    CANCELLED: 'badge-error',
  };
  private readonly statusBars: Record<string, string> = {
    PENDING: 'bg-warning',
    SHIPPED: 'bg-info',
    DELIVERED: 'bg-success',
    CANCELLED: 'bg-error',
  };

  constructor() {
    this.api.adminAnalytics().subscribe({
      next: (value) => this.analytics.set(value),
      error: () => {},
    });
  }

  statusBadge(status: string): string {
    return this.statusBadges[status] ?? 'badge-ghost';
  }

  statusBar(status: string): string {
    return this.statusBars[status] ?? 'bg-base-300';
  }

  statusShare(count: number): number {
    return Math.max(2, Math.round((count / this.maxStatusCount()) * 100));
  }
}
