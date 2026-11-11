import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { DeliveryArea } from '../../../core/models';
import { formatBdt } from '../../../core/format';

@Component({
  selector: 'app-admin-settings',
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-settings.component.html',
})
export class AdminSettingsComponent {
  private readonly api = inject(ApiService);
  readonly settings = signal<{ key: string; value: string; valueType: string }[]>([]);
  readonly areas = signal<DeliveryArea[]>([]);
  readonly message = signal<string | null>(null);
  readonly formatBdt = formatBdt;
  areaName = '';
  areaDistrict = '';
  areaCharge = 60;
  areaThreshold: number | null = 3000;

  constructor() { this.load(); }

  load(): void {
    this.api.adminSettings().subscribe({ next: (settings) => this.settings.set(settings) });
    this.api.adminDeliveryAreas().subscribe({ next: (areas) => this.areas.set(areas) });
  }

  saveSettings(): void {
    this.api.updateSettings(this.settings()).subscribe({
      next: (settings) => { this.settings.set(settings); this.message.set('Settings saved.'); },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not save settings.'),
    });
  }

  addArea(): void {
    if (!this.areaName.trim()) return;
    this.api.createDeliveryArea({
      name: this.areaName.trim(), district: this.areaDistrict.trim(), deliveryCharge: this.areaCharge,
      freeShippingThreshold: this.areaThreshold, slaDays: 5, active: true, displayOrder: this.areas().length,
    }).subscribe({
      next: () => { this.areaName = ''; this.areaDistrict = ''; this.load(); },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not create the area.'),
    });
  }

  removeArea(area: DeliveryArea): void {
    this.api.deleteDeliveryArea(area.id).subscribe({ next: () => this.load() });
  }
}
