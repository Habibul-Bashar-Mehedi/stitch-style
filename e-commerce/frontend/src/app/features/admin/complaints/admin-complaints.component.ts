import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Complaint } from '../../../core/models';

@Component({
  selector: 'app-admin-complaints',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './admin-complaints.component.html',
})
export class AdminComplaintsComponent {
  private readonly api = inject(ApiService);
  readonly complaints = signal<Complaint[]>([]);
  readonly message = signal<string | null>(null);
  responses: Record<number, string> = {};
  close: Record<number, boolean> = {};

  constructor() { this.load(); }

  load(): void {
    this.api.adminComplaints().subscribe({ next: (complaints) => this.complaints.set(complaints), error: () => this.message.set('Could not load complaints.') });
  }

  respond(complaint: Complaint): void {
    const response = this.responses[complaint.id];
    if (!response) return;
    this.api.respondComplaint(complaint.id, response, this.close[complaint.id] === true).subscribe({
      next: () => { this.responses[complaint.id] = ''; this.load(); },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not save the response.'),
    });
  }
}
