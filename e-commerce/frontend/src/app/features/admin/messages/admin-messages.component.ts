import { DatePipe } from '@angular/common';
import { Component, effect, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../../core/chat.service';
import { ChatThreadComponent } from '../../chat/chat-thread.component';

@Component({
  selector: 'app-admin-messages',
  imports: [DatePipe, FormsModule, ChatThreadComponent],
  templateUrl: './admin-messages.component.html',
})
export class AdminMessagesComponent implements OnInit, OnDestroy {
  readonly chat = inject(ChatService);
  readonly conversations = this.chat.conversations;
  readonly active = this.chat.active;
  readonly messages = this.chat.messages;
  readonly me = this.chat.me;
  readonly contacts = this.chat.contacts;
  readonly loading = signal(true);
  readonly type = signal('SUPPORT');
  readonly assignment = signal('all');
  readonly status = signal('OPEN');
  readonly query = signal('');
  readonly newInternalOpen = signal(false);
  readonly newRecipient = signal<number | null>(null);
  readonly newBody = signal('');

  constructor() {
    effect(() => {
      if (this.chat.refreshTick() > 0) this.reload(true);
    });
    this.chat.loadContacts();
    this.reload(false);
  }

  ngOnInit(): void {
    this.chat.connect();
  }

  ngOnDestroy(): void {
    this.chat.disconnect();
  }

  setType(type: string): void {
    this.type.set(type);
    this.reload(false);
  }

  apply(): void {
    this.reload(false);
  }

  select(id: number): void {
    this.chat.open(id);
  }

  send(body: string): void {
    this.chat.send(body, () => this.reload(true));
  }

  assign(): void {
    this.chat.assignToMe(() => this.reload(true));
  }

  close(): void {
    this.chat.close(() => this.reload(true));
  }

  openNewInternal(): void {
    this.chat.loadContacts();
    this.newInternalOpen.set(true);
  }

  createInternal(): void {
    const recipient = this.newRecipient();
    const body = this.newBody().trim();
    if (!recipient || !body) return;
    this.chat.createInternal(recipient, body, () => {
      this.newInternalOpen.set(false);
      this.newRecipient.set(null);
      this.newBody.set('');
      this.type.set('INTERNAL');
      this.reload(false);
    });
  }

  private reload(preserveActive: boolean): void {
    const current = this.chat.active()?.id;
    const filters: { type: string; status?: string; assigned?: string; q?: string } = { type: this.type() };
    if (this.type() === 'SUPPORT') {
      filters.status = this.status();
      if (this.assignment() !== 'all') filters.assigned = this.assignment();
    }
    if (this.query().trim()) filters.q = this.query().trim();
    this.chat.loadStaffConversations(filters, () => {
      this.loading.set(false);
      const list = this.conversations();
      const target = preserveActive && current && list.some((item) => item.id === current)
        ? current
        : list[0]?.id;
      if (target) this.chat.open(target);
    });
  }
}
