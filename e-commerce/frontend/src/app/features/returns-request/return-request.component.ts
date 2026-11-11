import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n.service';
import { formatBdt } from '../../core/format';
import { ReturnQuote, Tracking } from '../../core/models';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-return-request',
  imports: [DatePipe, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './return-request.component.html',
})
export class ReturnRequestComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(I18nService);
  readonly order = signal<Tracking | null>(null);
  readonly quote = signal<ReturnQuote | null>(null);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);
  readonly formatBdt = formatBdt;
  reason = '';
  quantities: Record<number, number> = {};
  conditions: Record<number, string> = {};

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      const orderNumber = params.get('order');
      if (orderNumber) {
        this.api.getMyOrder(orderNumber).subscribe({
          next: (order) => {
            this.order.set(order);
            for (const item of order.items) {
              this.quantities[item.orderItemId] = 0;
              this.conditions[item.orderItemId] = 'UNOPENED';
            }
            this.refreshQuote();
          },
        });
      }
    });
  }

  selectAll(): void {
    const order = this.order();
    if (!order) return;
    for (const item of order.items) {
      this.quantities[item.orderItemId] = item.quantity;
    }
    this.refreshQuote();
  }

  refreshQuote(): void {
    const order = this.order();
    if (!order) return;
    const items = order.items.map((item) => ({
      orderItemId: item.orderItemId,
      quantity: this.quantities[item.orderItemId] ?? 0,
    }));
    this.api.getReturnQuote(order.orderNumber, items).subscribe({
      next: (quote) => this.quote.set(quote),
      error: () => {},
    });
  }

  submit(): void {
    const order = this.order();
    if (!order) return;
    const items = order.items
      .filter((item) => (this.quantities[item.orderItemId] ?? 0) > 0)
      .map((item) => ({
        orderItemId: item.orderItemId,
        quantity: this.quantities[item.orderItemId],
        condition: this.conditions[item.orderItemId] ?? 'UNOPENED',
      }));
    if (!items.length) {
      this.error.set(this.i18n.t('return.selectItem'));
      return;
    }
    this.error.set(null);
    this.api.requestReturn(order.orderNumber, { orderNumber: order.orderNumber, reason: this.reason, items }).subscribe({
      next: () => this.submitted.set(true),
      error: (err) => this.error.set(err?.error?.detail ?? this.i18n.t('return.submitFailed')),
    });
  }
}
