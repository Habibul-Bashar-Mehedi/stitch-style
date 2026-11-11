import { computed, inject, Injectable, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { forkJoin } from 'rxjs';
import { ChatApiService } from './chat-api.service';
import { AuthService } from './auth.service';
import { ChatUnreadService } from './chat-unread.service';
import { ChatContact, ChatConversation, ChatEvent, ChatMessage, ChatUser } from './models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly api = inject(ChatApiService);
  private readonly auth = inject(AuthService);
  private readonly unread = inject(ChatUnreadService);

  readonly conversations = signal<ChatConversation[]>([]);
  readonly messages = signal<ChatMessage[]>([]);
  readonly active = signal<ChatConversation | null>(null);
  readonly contacts = signal<ChatContact[]>([]);
  readonly typing = signal<string | null>(null);
  readonly connected = signal(false);
  readonly refreshTick = signal(0);
  private client?: Client;
  private typingTimer?: ReturnType<typeof setTimeout>;
  private lastTypingAt = 0;
  readonly unreadTotal = computed(() =>
    this.conversations().reduce((total, conversation) => total + conversation.unreadCount, 0),
  );
  readonly me = computed<ChatUser | null>(() => {
    const user = this.auth.me();
    if (!user) return null;
    return { id: user.id, fullName: user.fullName, role: user.role, pictureUrl: user.pictureUrl };
  });
  readonly isStaff = computed(() => {
    const role = this.auth.me()?.role;
    return role === 'ADMIN' || role === 'MANAGER';
  });

  refreshUnread(): void {
    const user = this.auth.me();
    if (!user) {
      this.conversations.set([]);
      return;
    }
    if (user.role === 'CUSTOMER') {
      this.loadCustomerConversations();
    }
  }

  loadCustomerConversations(onDone?: () => void): void {
    this.api.getConversations().subscribe({
      next: (conversations) => {
        this.conversations.set(conversations);
        this.unread.set(conversations.reduce((total, item) => total + item.unreadCount, 0));
        onDone?.();
      },
      error: () => onDone?.(),
    });
  }

  loadStaffConversations(filters: { type: string; status?: string; assigned?: string; q?: string }, onDone?: () => void): void {
    this.api.staffConversations(filters).subscribe({
      next: (conversations) => {
        this.conversations.set(conversations);
        this.unread.set(conversations.reduce((total, item) => total + item.unreadCount, 0));
        onDone?.();
      },
      error: () => onDone?.(),
    });
  }

  open(conversationId: number, onDone?: () => void): void {
    const staff = this.isStaff();
    const conversation$ = staff ? this.api.staffConversation(conversationId) : this.api.getConversation(conversationId);
    const messages$ = staff ? this.api.staffMessages(conversationId) : this.api.getMessages(conversationId);
    forkJoin({ conversation: conversation$, messages: messages$ }).subscribe({
      next: ({ conversation, messages }) => {
        this.active.set(conversation);
        this.typing.set(null);
        this.messages.set(messages.items);
        this.markRead();
        onDone?.();
      },
      error: () => onDone?.(),
    });
  }

  send(body: string, onDone?: () => void): void {
    const conversation = this.active();
    if (!conversation || !body.trim()) return;
    const payload = { clientMessageId: this.clientId(), body: body.trim() };
    const call = this.isStaff()
      ? this.api.staffSend(conversation.id, payload)
      : this.api.send(conversation.id, payload);
    call.subscribe({
      next: (message) => {
        this.messages.update((messages) => [...messages, message]);
        this.active.update((current) => current
          ? { ...current, lastMessagePreview: message.body, lastMessageAt: message.createdAt }
          : current);
        onDone?.();
      },
      error: () => onDone?.(),
    });
  }

  createSupport(body: string, orderNumber: string | null, onDone?: (conversation: ChatConversation) => void): void {
    if (!body.trim()) return;
    this.api.createSupport({
      orderNumber: orderNumber || null,
      clientMessageId: this.clientId(),
      body: body.trim(),
    }).subscribe({
      next: (conversation) => {
        this.open(conversation.id);
        onDone?.(conversation);
      },
    });
  }

  createInternal(recipientUserId: number, body: string, onDone?: (conversation: ChatConversation) => void): void {
    if (!body.trim()) return;
    this.api.createInternal({
      recipientUserId,
      clientMessageId: this.clientId(),
      body: body.trim(),
    }).subscribe({
      next: (conversation) => {
        this.open(conversation.id);
        onDone?.(conversation);
      },
    });
  }

  assignToMe(onDone?: () => void): void {
    const conversation = this.active();
    const user = this.auth.me();
    if (!conversation || !user) return;
    this.api.assign(conversation.id, user.id).subscribe({ next: () => this.open(conversation.id, onDone) });
  }

  close(onDone?: () => void): void {
    const conversation = this.active();
    if (!conversation) return;
    this.api.close(conversation.id).subscribe({ next: () => this.open(conversation.id, onDone) });
  }

  loadContacts(): void {
    this.api.contacts().subscribe({ next: (contacts) => this.contacts.set(contacts) });
  }

  markRead(): void {
    const conversation = this.active();
    const last = this.messages().at(-1);
    if (!conversation || !last) return;
    const call = this.isStaff()
      ? this.api.staffMarkRead(conversation.id, last.id)
      : this.api.markRead(conversation.id, last.id);
    call.subscribe({
      next: () => {
        this.conversations.update((list) => list.map((item) => item.id === conversation.id
          ? { ...item, unreadCount: 0 }
          : item));
        this.unread.set(this.conversations().reduce((total, item) => total + item.unreadCount, 0));
      },
    });
  }

  connect(): void {
    if (typeof window === 'undefined' || this.client?.active) return;
    if (!this.auth.me()) return;
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected.set(true);
        this.client?.subscribe('/user/queue/chat', (frame) => this.handleEvent(JSON.parse(frame.body) as ChatEvent));
        const active = this.active();
        if (active) this.open(active.id);
        this.refreshTick.update((value) => value + 1);
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = undefined;
    this.connected.set(false);
    this.typing.set(null);
  }

  typingStarted(): void {
    const conversation = this.active();
    if (!conversation || conversation.status !== 'OPEN' || !this.client?.connected) return;
    const now = Date.now();
    if (now - this.lastTypingAt < 1200) return;
    this.lastTypingAt = now;
    this.client.publish({ destination: `/app/chat/${conversation.id}/typing`, body: '{}' });
  }

  private handleEvent(event: ChatEvent): void {
    if (event.type === 'MESSAGE_CREATED' && event.message) {
      const message = event.message;
      if (this.active()?.id === event.conversationId) {
        if (!this.messages().some((item) => item.id === message.id)) {
          this.messages.update((messages) => [...messages, message]);
        }
        this.active.update((current) => current
          ? { ...current, lastMessagePreview: message.body, lastMessageAt: message.createdAt }
          : current);
        this.conversations.update((list) => list.map((item) => item.id === event.conversationId
          ? { ...item, lastMessagePreview: message.body, lastMessageAt: message.createdAt, unreadCount: 0 }
          : item));
        this.markRead();
      } else {
        this.conversations.update((list) => list.map((item) => item.id === event.conversationId
          ? { ...item, lastMessagePreview: message.body, lastMessageAt: message.createdAt,
              unreadCount: item.unreadCount + 1 }
          : item));
        this.unread.set(this.conversations().reduce((total, item) => total + item.unreadCount, 0));
        this.refreshTick.update((value) => value + 1);
      }
      return;
    }
    if (event.type === 'MESSAGE_READ' && event.readerId !== this.me()?.id
        && this.active()?.id === event.conversationId && event.lastReadMessageId != null) {
      const lastRead = event.lastReadMessageId;
      this.messages.update((messages) => messages.map((message) => message.sender.id === this.me()?.id
        && message.id <= lastRead ? { ...message, readByOthers: true } : message));
      return;
    }
    if (event.type === 'TYPING' && this.active()?.id === event.conversationId) {
      if (event.typingUser && event.typingUser !== this.me()?.fullName) {
        this.typing.set(event.typingUser);
        if (this.typingTimer) clearTimeout(this.typingTimer);
        this.typingTimer = setTimeout(() => this.typing.set(null), 4000);
      }
      return;
    }
    if (event.type === 'CONVERSATION_UPDATED') {
      this.refreshTick.update((value) => value + 1);
    }
  }

  private clientId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
