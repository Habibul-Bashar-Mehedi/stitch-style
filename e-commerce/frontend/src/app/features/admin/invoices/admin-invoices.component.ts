import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Invoice } from '../../../core/models';
import { formatBdt } from '../../../core/format';

@Component({
  selector: 'app-admin-invoices',
  imports: [DatePipe, RouterLink],
  templateUrl: './admin-invoices.component.html',
})
export class AdminInvoicesComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly invoices = signal<Invoice[]>([]);
  readonly formatBdt = formatBdt;

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      const ids = (params.get('ids') ?? '').split(',').map(Number).filter((id) => id > 0);
      if (ids.length) {
        this.api.adminInvoices(ids).subscribe({ next: (invoices) => this.invoices.set(invoices) });
      }
    });
  }

  print(): void {
    if (typeof window !== 'undefined') window.print();
  }
}
