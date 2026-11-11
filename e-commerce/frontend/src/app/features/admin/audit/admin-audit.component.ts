import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuditLog } from '../../../core/models';

@Component({
  selector: 'app-admin-audit',
  imports: [FormsModule, RouterLink, DatePipe, JsonPipe],
  templateUrl: './admin-audit.component.html',
})
export class AdminAuditComponent {
  private readonly api = inject(ApiService);
  readonly logs = signal<AuditLog[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly message = signal<string | null>(null);
  actor = '';
  entityType = '';

  constructor() { this.load(); }

  load(): void {
    this.api.adminAudit({ page: this.page(), size: 30, actor: this.actor || undefined, entityType: this.entityType || undefined }).subscribe({
      next: (page) => { this.logs.set(page.content); this.totalPages.set(page.totalPages); },
      error: () => this.message.set('Could not load the audit log.'),
    });
  }

  changePage(delta: number): void {
    this.page.set(Math.max(0, this.page() + delta));
    this.load();
  }
}
