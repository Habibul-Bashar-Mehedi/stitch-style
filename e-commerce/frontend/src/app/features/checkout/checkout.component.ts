import { NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { DeliveryArea, OrderResponse, PaymentMethod } from '../../core/models';
import { formatBdt } from '../../core/format';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-checkout',
  imports: [NgClass, ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: './checkout.component.html',
})
export class CheckoutComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly cart = inject(CartService);
  readonly formatBdt = formatBdt;

  readonly areas = signal<DeliveryArea[]>([]);
  readonly methods = signal<PaymentMethod[]>([]);
  readonly order = signal<OrderResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly couponDiscount = signal(0);
  readonly couponMessage = signal<string | null>(null);
  readonly selected = computed(() => this.cart.selectedLines());

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    phone: ['', [Validators.required, Validators.pattern(/^(?:\+8801[3-9]\d{8}|01[3-9]\d{8})$/)]],
    address: ['', [Validators.required, Validators.minLength(8)]],
    areaId: [0, [Validators.required, Validators.min(1)]],
    paymentMethod: ['COD', [Validators.required]],
    note: [''],
    couponCode: [''],
  });

  constructor() {
    this.api.getDeliveryAreas().subscribe({
      next: (areas) => {
        this.areas.set(areas);
        if (areas.length > 0) this.form.patchValue({ areaId: areas[0].id });
      },
    });
    this.api.getPaymentMethods().subscribe({
      next: (methods) => {
        this.methods.set(methods.filter((method) => method.enabled));
        if (!methods.some((method) => method.enabled && method.code === 'COD')) {
          const first = methods.find((method) => method.enabled);
          if (first) this.form.patchValue({ paymentMethod: first.code });
        }
      },
    });
    this.cart.refresh();
  }

  selectedArea(): DeliveryArea | undefined {
    return this.areas().find((area) => area.id === Number(this.form.controls.areaId.value));
  }

  deliveryEstimate(): number {
    const area = this.selectedArea();
    if (!area) return 0;
    if (area.freeShippingThreshold !== undefined && this.cart.cart().subtotal >= area.freeShippingThreshold) return 0;
    return area.deliveryCharge;
  }

  submit(): void {
    if (this.form.invalid || this.selected().length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const idempotencyKey = this.newIdempotencyKey();
    this.api.placeOrder({
      customer: { name: value.name, phone: value.phone },
      shippingAddress: value.address,
      deliveryAreaId: Number(value.areaId),
      paymentMethod: value.paymentMethod as 'COD' | 'BKASH' | 'SSLCOMMERZ',
      note: value.note || undefined,
      couponCode: value.couponCode || undefined,
      items: this.selected().map((line) => ({
        variantId: line.variantId,
        quantity: line.quantity,
        selected: true,
      })),
    }, idempotencyKey).subscribe({
      next: (order) => {
        this.order.set(order);
        this.cart.removeSelected();
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.detail ?? 'Could not place the order. Please review your details and try again.');
      },
    });
  }

  applyCoupon(): void {
    const code = this.form.controls.couponCode.value.trim();
    if (!code) return;
    this.api.validateCoupon(code, this.cart.cart().subtotal).subscribe({
      next: (result) => {
        this.couponDiscount.set(result.discount);
        this.couponMessage.set(`${result.code}: ${result.message} (−৳${result.discount})`);
      },
      error: (err) => {
        this.couponDiscount.set(0);
        this.couponMessage.set(err?.error?.detail ?? 'Coupon could not be applied.');
      },
    });
  }

  total(): number {
    return Math.max(0, this.cart.cart().subtotal + this.deliveryEstimate() - this.couponDiscount());
  }

  private newIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
    return `idem-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  }
}
