import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from './api-base';
import { ChatContact, ChatConversation, ChatMessage, ChatMessagePage } from './models';

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  getConversations() {
    return this.http.get<ChatConversation[]>(this.url('/api/v1/me/chat/conversations'));
  }

  createSupport(payload: { orderNumber?: string | null; clientMessageId: string; body: string }) {
    return this.http.post<ChatConversation>(this.url('/api/v1/me/chat/conversations'), payload);
  }

  getConversation(id: number) {
    return this.http.get<ChatConversation>(this.url(`/api/v1/me/chat/conversations/${id}`));
  }

  getMessages(id: number, beforeId?: number) {
    return this.http.get<ChatMessagePage>(this.url(`/api/v1/me/chat/conversations/${id}/messages`), {
      params: this.params({ beforeId }),
    });
  }

  send(id: number, payload: { clientMessageId: string; body: string }) {
    return this.http.post<ChatMessage>(this.url(`/api/v1/me/chat/conversations/${id}/messages`), payload);
  }

  markRead(id: number, lastReadMessageId: number) {
    return this.http.post<void>(this.url(`/api/v1/me/chat/conversations/${id}/read`), { lastReadMessageId });
  }

  staffConversations(params: Record<string, string | number | undefined> = {}) {
    return this.http.get<ChatConversation[]>(this.url('/api/v1/admin/chat/conversations'), { params: this.params(params) });
  }

  staffConversation(id: number) {
    return this.http.get<ChatConversation>(this.url(`/api/v1/admin/chat/conversations/${id}`));
  }

  staffMessages(id: number, beforeId?: number) {
    return this.http.get<ChatMessagePage>(this.url(`/api/v1/admin/chat/conversations/${id}/messages`), {
      params: this.params({ beforeId }),
    });
  }

  staffSend(id: number, payload: { clientMessageId: string; body: string }) {
    return this.http.post<ChatMessage>(this.url(`/api/v1/admin/chat/conversations/${id}/messages`), payload);
  }

  staffMarkRead(id: number, lastReadMessageId: number) {
    return this.http.post<void>(this.url(`/api/v1/admin/chat/conversations/${id}/read`), { lastReadMessageId });
  }

  assign(id: number, staffUserId: number) {
    return this.http.patch<ChatConversation>(this.url(`/api/v1/admin/chat/conversations/${id}/assign`), { staffUserId });
  }

  close(id: number) {
    return this.http.post<ChatConversation>(this.url(`/api/v1/admin/chat/conversations/${id}/close`), {});
  }

  contacts() {
    return this.http.get<ChatContact[]>(this.url('/api/v1/admin/chat/contacts'));
  }

  createInternal(payload: { recipientUserId: number; clientMessageId: string; body: string }) {
    return this.http.post<ChatConversation>(this.url('/api/v1/admin/chat/internal'), payload);
  }

  private url(path: string): string {
    return `${this.base}${path}`;
  }

  private params(values: Record<string, string | number | undefined>): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(values)) {
      if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    }
    return params;
  }
}
