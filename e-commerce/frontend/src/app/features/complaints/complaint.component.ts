import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-complaint',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './complaint.component.html',
})
export class ComplaintComponent {
  private readonly api = inject(ApiService);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);
  name = '';
  phone = '';
  orderNumber = '';
  message = '';

  submit(): void {
    if (!this.name.trim() || !this.phone.trim() || !this.message.trim()) return;
    this.api.submitComplaint({
      name: this.name.trim(), phone: this.phone.trim(),
      orderNumber: this.orderNumber.trim() || undefined, message: this.message.trim(),
    }).subscribe({
      next: () => this.submitted.set(true),
      error: (err) => this.error.set(err?.error?.detail ?? 'Could not submit the complaint.'),
    });
  }
}
