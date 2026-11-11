import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Tracking } from '../../core/models';
import { formatBdt } from '../../core/format';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-tracking',
  imports: [FormsModule, DatePipe, TranslatePipe],
  templateUrl: './tracking.component.html',
})
export class TrackingComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly tracking = signal<Tracking | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly formatBdt = formatBdt;

  token = '';
  orderNumber = '';
  phone = '';

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      const token = params.get('token');
      if (token) {
        this.token = token;
        this.trackToken();
      }
    });
  }

  trackToken(): void {
    if (!this.token.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.track(this.token.trim()).subscribe({
      next: (tracking) => {
        this.tracking.set(tracking);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('We could not find an order for that tracking token.');
      },
    });
  }

  trackPhone(): void {
    if (!this.orderNumber.trim() || !this.phone.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.trackByPhone(this.orderNumber.trim(), this.phone.trim()).subscribe({
      next: (tracking) => {
        this.tracking.set(tracking);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No order matches that order number and phone number.');
      },
    });
  }
}
