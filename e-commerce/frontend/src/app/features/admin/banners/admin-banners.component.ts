import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Banner, Notice } from '../../../core/models';

@Component({
  selector: 'app-admin-banners',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './admin-banners.component.html',
})
export class AdminBannersComponent {
  private readonly api = inject(ApiService);
  readonly banners = signal<Banner[]>([]);
  readonly notices = signal<Notice[]>([]);
  readonly message = signal<string | null>(null);

  readonly bannerFormOpen = signal(false);
  readonly noticeFormOpen = signal(false);

  readonly pinnedNotices = computed(() => this.notices().filter((notice) => notice.priority > 0).length);
  readonly scheduledCount = computed(() =>
    [...this.banners(), ...this.notices()].filter((item) => item.startsAt || item.endsAt).length,
  );

  bannerTitle = '';
  bannerLink = '';
  bannerFile: File | null = null;
  noticeTitle = '';
  noticeBody = '';
  noticePriority = 0;

  constructor() { this.load(); }

  load(): void {
    this.api.adminBanners().subscribe({ next: (banners) => this.banners.set(banners) });
    this.api.adminNotices().subscribe({ next: (notices) => this.notices.set(notices) });
  }

  startBannerForm(): void {
    this.bannerFormOpen.set(true);
    this.bannerTitle = '';
    this.bannerLink = '';
    this.bannerFile = null;
  }

  closeBannerForm(): void {
    this.bannerFormOpen.set(false);
    this.bannerTitle = '';
    this.bannerLink = '';
    this.bannerFile = null;
  }

  startNoticeForm(): void {
    this.noticeFormOpen.set(true);
    this.noticeTitle = '';
    this.noticeBody = '';
    this.noticePriority = 0;
  }

  closeNoticeForm(): void {
    this.noticeFormOpen.set(false);
    this.noticeTitle = '';
    this.noticeBody = '';
    this.noticePriority = 0;
  }

  onFile(event: Event): void {
    this.bannerFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  createBanner(): void {
    if (!this.bannerFile || !this.bannerTitle.trim()) {
      this.message.set('A title and an image are required.');
      return;
    }
    this.api.uploadMedia(this.bannerFile).subscribe({
      next: (media) => {
        this.api.createBanner({
          title: this.bannerTitle.trim(), imageMediaId: media.id, linkUrl: this.bannerLink.trim() || null,
          displayOrder: this.banners().length, active: true,
        }).subscribe({
          next: () => {
            this.bannerFormOpen.set(false);
            this.bannerTitle = '';
            this.bannerLink = '';
            this.bannerFile = null;
            this.load();
          },
          error: (err) => this.message.set(err?.error?.detail ?? 'Could not create the banner.'),
        });
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not upload the image.'),
    });
  }

  createNotice(): void {
    if (!this.noticeTitle.trim() || !this.noticeBody.trim()) return;
    this.api.createNotice({
      title: this.noticeTitle.trim(), body: this.noticeBody.trim(), priority: this.noticePriority, active: true,
    }).subscribe({
      next: () => {
        this.noticeFormOpen.set(false);
        this.noticeTitle = '';
        this.noticeBody = '';
        this.noticePriority = 0;
        this.load();
      },
      error: (err) => this.message.set(err?.error?.detail ?? 'Could not create the notice.'),
    });
  }

  removeBanner(banner: Banner): void { this.api.deleteBanner(banner.id).subscribe({ next: () => this.load() }); }
  removeNotice(notice: Notice): void { this.api.deleteNotice(notice.id).subscribe({ next: () => this.load() }); }
}
