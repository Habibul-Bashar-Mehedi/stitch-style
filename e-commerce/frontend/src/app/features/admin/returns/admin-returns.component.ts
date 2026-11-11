import { DatePipe, NgClass } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { ReturnRecord } from '../../../core/models';
import { formatBdt } from '../../../core/format';

@Component({
  selector: 'app-admin-returns',
  imports: [FormsModule, RouterLink, DatePipe, NgClass],
  templateUrl: './admin-returns.component.html',
})
export class AdminReturnsComponent {
  private readonly api = inject(ApiService);
  readonly returns = signal<ReturnRecord[]>([]);
  readonly message = signal<string | null>(null);
  readonly formatBdt = formatBdt;
  refundMethod: Record<number, string> = {};
  refundAmount: Record<number, number | null> = {};

  private readonly badges: Record<string, string> = {
    REQUESTED: 'badge-warning',
    APPROVED: 'badge-info',
    REJECTED: 'badge-error',
    RECEIVED: 'badge-primary',
    REFUNDED: 'badge-success',
  };

  constructor() { this.load(); }

  load(): void {
    this.api.adminReturns().subscribe({
      next: (returns) => {
        this.returns.set(returns);
        for (const item of returns) {
          if (item.status === 'RECEIVED' && this.refundAmount[item.id] == null) {
            this.refundAmount[item.id] = item.quote?.total ?? null;
          }
          if (item.status === 'RECEIVED' && this.refundMethod[item.id] == null) {
            this.refundMethod[item.id] = 'MANUAL';
          }
        }
      },
      error: () => this.message.set('Could not load returns.'),
    });
  }

  returnBadge(status: string): string {
    return this.badges[status] ?? 'badge-ghost';
  }

  advance(item: ReturnRecord, status: string): void {
    let note: string | undefined;
    if (status === 'REJECTED') {
      const reason = window.prompt('Reason for rejection (optional):', '');
      if (reason === null) return;
      note = reason.trim() || undefined;
    }
    this.api.advanceReturn(item.id, status, note).subscribe({
      next: () => this.load(),
      error: (err) => this.message.set(err?.error?.detail ?? 'Transition failed.'),
    });
  }

  refund(item: ReturnRecord): void {
    const amount = this.refundAmount[item.id] ?? null;
    const max = item.quote?.total ?? 0;
    if (amount == null || amount < 0.01 || amount > max) {
      this.message.set(`Refund amount must be between ৳0.01 and ${this.formatBdt(max)}.`);
      return;
    }
    this.api.refundReturn(item.id, this.refundMethod[item.id] ?? 'MANUAL', amount).subscribe({
      next: () => this.load(),
      error: (err) => this.message.set(err?.error?.detail ?? 'Refund failed.'),
    });
  }
}
