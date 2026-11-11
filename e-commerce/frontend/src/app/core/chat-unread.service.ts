import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from './api-base';
import { ChatConversation } from './models';

@Injectable({ providedIn: 'root' })
export class ChatUnreadService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  readonly count = signal(0);

  refresh(): void {
    if (typeof window === 'undefined') return;
    this.http.get<ChatConversation[]>(`${this.base}/api/v1/me/chat/conversations`).subscribe({
      next: (conversations) => this.set(conversations.reduce((total, item) => total + item.unreadCount, 0)),
      error: () => {},
    });
  }

  set(count: number): void {
    this.count.set(Math.max(0, count));
  }
}
