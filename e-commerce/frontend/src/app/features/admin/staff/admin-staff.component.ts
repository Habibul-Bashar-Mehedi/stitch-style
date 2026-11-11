import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Staff } from '../../../core/models';

@Component({
  selector: 'app-admin-staff',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './admin-staff.component.html',
})
export class AdminStaffComponent {
  private readonly api = inject(ApiService);
  readonly staff = signal<Staff[]>([]);
  readonly message = signal<string | null>(null);
  email = '';
  fullName = '';
  role: 'ADMIN' | 'MANAGER' = 'MANAGER';

  constructor() { this.load(); }

  load(): void {
    this.api.adminStaff().subscribe({ next: (staff) => this.staff.set(staff), error: () => this.message.set('Could not load staff.') });
  }

  create(): void {
    if (!this.email.trim()) return;
    this.api.createStaff({ email: this.email.trim(), fullName: this.fullName.trim(), role: this.role, active: true }).subscribe({
      next: () => { this.email = ''; this.fullName = ''; this.load(); },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not create the staff account.'),
    });
  }

  toggle(member: Staff): void {
    this.api.setStaffActive(member.id, !member.active).subscribe({
      next: () => this.load(),
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not change the account.'),
    });
  }
}
