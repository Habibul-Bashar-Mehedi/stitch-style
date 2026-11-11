import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { I18nService } from '../../core/i18n.service';

@Component({
  selector: 'app-legal',
  templateUrl: './legal.component.html',
})
export class LegalComponent {
  private readonly route = inject(ActivatedRoute);
  readonly i18n = inject(I18nService);

  get title(): string {
    return (this.route.snapshot.data['title'] as string) ?? 'Information';
  }

  get slug(): string {
    return this.route.snapshot.routeConfig?.path ?? 'info';
  }
}
