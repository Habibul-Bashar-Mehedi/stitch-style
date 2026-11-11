import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Coupon } from '../../../core/models';
import { formatBdt } from '../../../core/format';

@Component({
  selector: 'app-admin-coupons',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './admin-coupons.component.html',
})
export class AdminCouponsComponent {
  private readonly api = inject(ApiService);
  readonly coupons = signal<Coupon[]>([]);
  readonly message = signal<string | null>(null);
  readonly formatBdt = formatBdt;
  code = '';
  discountType = 'PERCENT';
  discountValue = 10;
  minSubtotal: number | null = 0;
  maxUses: number | null = 100;

  constructor() { this.load(); }

  load(): void {
    this.api.adminCoupons().subscribe({ next: (coupons) => this.coupons.set(coupons), error: () => this.message.set('Could not load coupons.') });
  }

  create(): void {
    if (!this.code.trim()) return;
    this.api.createCoupon({
      code: this.code.trim(), discountType: this.discountType, discountValue: this.discountValue,
      minSubtotal: this.minSubtotal, maxUses: this.maxUses, active: true,
    }).subscribe({
      next: () => { this.code = ''; this.load(); },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not create the coupon.'),
    });
  }

  remove(coupon: Coupon): void {
    this.api.deleteCoupon(coupon.id).subscribe({ next: () => this.load() });
  }
}
