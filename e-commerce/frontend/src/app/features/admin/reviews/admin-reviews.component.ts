import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Review } from '../../../core/models';

@Component({
  selector: 'app-admin-reviews',
  imports: [RouterLink, DatePipe],
  templateUrl: './admin-reviews.component.html',
})
export class AdminReviewsComponent {
  private readonly api = inject(ApiService);
  readonly reviews = signal<Review[]>([]);
  readonly message = signal<string | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.api.adminReviews().subscribe({ next: (reviews) => this.reviews.set(reviews), error: () => this.message.set('Could not load reviews.') });
  }

  moderate(review: Review, status: string): void {
    this.api.moderateReview(review.id, status).subscribe({ next: () => this.load(), error: (err) => this.message.set(err?.error?.detail ?? 'Moderation failed.') });
  }
}
