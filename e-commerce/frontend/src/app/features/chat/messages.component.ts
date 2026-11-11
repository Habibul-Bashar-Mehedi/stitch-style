import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../core/chat.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { ChatThreadComponent } from '../chat/chat-thread.component';

@Component({
  selector: 'app-messages',
  imports: [DatePipe, FormsModule, TranslatePipe, ChatThreadComponent],
  templateUrl: './messages.component.html',
})
export class MessagesComponent implements OnInit, OnDestroy {
  readonly chat = inject(ChatService);
  readonly conversations = this.chat.conversations;
  readonly active = this.chat.active;
  readonly messages = this.chat.messages;
  readonly me = this.chat.me;
  readonly loading = signal(true);
  readonly draft = signal('');
  readonly hasOpen = computed(() =>
    this.conversations().some((conversation) => conversation.type === 'SUPPORT' && conversation.status === 'OPEN'),
  );

  constructor() {
    effect(() => {
      if (this.chat.refreshTick() > 0) this.chat.loadCustomerConversations();
    });
    this.chat.loadCustomerConversations(() => {
      this.loading.set(false);
      const open = this.conversations().find((conversation) => conversation.status === 'OPEN')
        ?? this.conversations()[0];
      if (open) this.chat.open(open.id);
    });
  }

  ngOnInit(): void {
    this.chat.connect();
  }

  ngOnDestroy(): void {
    this.chat.disconnect();
  }

  select(id: number): void {
    this.chat.open(id);
  }

  send(body: string): void {
    this.chat.send(body, () => this.chat.loadCustomerConversations());
  }

  start(): void {
    const body = this.draft().trim();
    if (!body) return;
    this.chat.createSupport(body, null, () => {
      this.draft.set('');
      this.chat.loadCustomerConversations();
    });
  }
}
