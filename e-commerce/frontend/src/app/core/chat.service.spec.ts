import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { ChatApiService } from './chat-api.service';
import { ChatService } from './chat.service';
import { ChatUnreadService } from './chat-unread.service';
import { ChatConversation, ChatEvent, ChatMessage, Me } from './models';

function conversation(overrides: Partial<ChatConversation> = {}): ChatConversation {
  return {
    id: 1,
    type: 'SUPPORT',
    status: 'OPEN',
    counterpart: null,
    assignedStaff: null,
    orderNumber: null,
    lastMessagePreview: null,
    lastMessageAt: null,
    unreadCount: 0,
    assignedToMe: false,
    ...overrides,
  };
}

function message(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 1,
    conversationId: 1,
    sender: { id: 1, fullName: 'Nusrat', role: 'CUSTOMER' },
    body: 'Hello',
    clientMessageId: 'c1',
    createdAt: '2026-01-01T00:00:00Z',
    readByOthers: false,
    ...overrides,
  };
}

function event(overrides: Partial<ChatEvent>): ChatEvent {
  return { eventId: 'e', type: 'CONVERSATION_UPDATED', conversationId: 1, ...overrides } as ChatEvent;
}

describe('ChatService', () => {
  let api: any;
  let me: any;
  let unread: { set: ReturnType<typeof vi.fn> };
  let service: ChatService;

  beforeEach(() => {
    api = {
      getConversations: vi.fn(() => of([])),
      staffConversations: vi.fn(() => of([])),
      getConversation: vi.fn(() => of(conversation())),
      staffConversation: vi.fn(() => of(conversation())),
      getMessages: vi.fn(() => of({ items: [message()], hasMore: false })),
      staffMessages: vi.fn(() => of({ items: [message()], hasMore: false })),
      send: vi.fn(() => of(message({ id: 2, body: 'Reply' }))),
      staffSend: vi.fn(() => of(message({ id: 2, body: 'Reply' }))),
      markRead: vi.fn(() => of(undefined)),
      staffMarkRead: vi.fn(() => of(undefined)),
      createSupport: vi.fn(() => of(conversation())),
      createInternal: vi.fn(() => of(conversation({ type: 'INTERNAL' }))),
      assign: vi.fn(() => of(conversation())),
      close: vi.fn(() => of(conversation({ status: 'CLOSED' }))),
      contacts: vi.fn(() => of([])),
    };
    me = signal<Me | null>(null);
    unread = { set: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        ChatService,
        { provide: ChatApiService, useValue: api },
        { provide: AuthService, useValue: { me } },
        { provide: ChatUnreadService, useValue: unread },
      ],
    });
    service = TestBed.inject(ChatService);
  });

  it('derives unread totals and the current user', () => {
    service.conversations.set([conversation({ unreadCount: 2 }), conversation({ id: 2, unreadCount: 3 })]);
    expect(service.unreadTotal()).toBe(5);
    expect(service.me()).toBeNull();

    me.set({ id: 1, fullName: 'Nusrat', email: 'n@x.com', role: 'CUSTOMER', pictureUrl: '/p.png' });
    expect(service.me()).toEqual({ id: 1, fullName: 'Nusrat', role: 'CUSTOMER', pictureUrl: '/p.png' });
    expect(service.isStaff()).toBe(false);

    me.set({ id: 2, fullName: 'Admin', email: 'a@x.com', role: 'ADMIN' });
    expect(service.isStaff()).toBe(true);
  });

  it('clears conversations when signed out and loads them for customers', () => {
    service.conversations.set([conversation()]);
    service.refreshUnread();
    expect(service.conversations()).toEqual([]);
    expect(api.getConversations).not.toHaveBeenCalled();

    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    api.getConversations.mockReturnValue(of([conversation({ unreadCount: 4 })]));
    const done = vi.fn();
    service.refreshUnread();
    expect(service.conversations().length).toBe(1);
    expect(unread.set).toHaveBeenCalledWith(4);
    service.loadCustomerConversations(done);
    expect(done).toHaveBeenCalled();
  });

  it('loads staff conversations with filters', () => {
    me.set({ id: 2, fullName: 'Admin', email: 'a@x.com', role: 'ADMIN' });
    api.staffConversations.mockReturnValue(of([conversation({ unreadCount: 1 })]));
    const done = vi.fn();

    service.loadStaffConversations({ type: 'SUPPORT', q: 'nusrat' }, done);

    expect(api.staffConversations).toHaveBeenCalledWith({ type: 'SUPPORT', q: 'nusrat' });
    expect(service.conversations().length).toBe(1);
    expect(unread.set).toHaveBeenCalledWith(1);
    expect(done).toHaveBeenCalled();
  });

  it('opens a customer conversation and marks the last message read', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    const done = vi.fn();

    service.open(1, done);

    expect(api.getConversation).toHaveBeenCalledWith(1);
    expect(api.getMessages).toHaveBeenCalledWith(1);
    expect(service.active()?.id).toBe(1);
    expect(service.messages().length).toBe(1);
    expect(api.markRead).toHaveBeenCalledWith(1, 1);
    expect(done).toHaveBeenCalled();
  });

  it('opens staff conversations through the staff endpoints', () => {
    me.set({ id: 2, fullName: 'Admin', email: 'a@x.com', role: 'ADMIN' });

    service.open(1);

    expect(api.staffConversation).toHaveBeenCalledWith(1);
    expect(api.staffMessages).toHaveBeenCalledWith(1);
    expect(api.staffMarkRead).toHaveBeenCalledWith(1, 1);
  });

  it('sends trimmed messages and updates the active preview', () => {
    service.active.set(conversation());
    service.messages.set([]);

    service.send('  Hello  ');

    expect(api.send).toHaveBeenCalledWith(1, expect.objectContaining({ body: 'Hello' }));
    expect(service.messages().at(-1)?.body).toBe('Reply');
    expect(service.active()?.lastMessagePreview).toBe('Reply');

    api.send.mockClear();
    service.send('   ');
    expect(api.send).not.toHaveBeenCalled();
  });

  it('sends through the staff endpoint for staff users', () => {
    me.set({ id: 2, fullName: 'Admin', email: 'a@x.com', role: 'ADMIN' });
    service.active.set(conversation());

    service.send('Reply');

    expect(api.staffSend).toHaveBeenCalled();
    expect(api.send).not.toHaveBeenCalled();
  });

  it('creates support and internal conversations then opens them', () => {
    service.createSupport(' Help ', 'ORD-1');
    expect(api.createSupport).toHaveBeenCalledWith(expect.objectContaining({ orderNumber: 'ORD-1', body: 'Help' }));
    expect(api.getConversation).toHaveBeenCalled();

    service.createInternal(9, ' DM ');
    expect(api.createInternal).toHaveBeenCalledWith(expect.objectContaining({ recipientUserId: 9, body: 'DM' }));

    api.createSupport.mockClear();
    service.createSupport('   ', null);
    expect(api.createSupport).not.toHaveBeenCalled();
  });

  it('assigns and closes the active conversation', () => {
    me.set({ id: 2, fullName: 'Admin', email: 'a@x.com', role: 'ADMIN' });
    service.active.set(conversation());

    service.assignToMe();
    expect(api.assign).toHaveBeenCalledWith(1, 2);

    service.close();
    expect(api.close).toHaveBeenCalledWith(1);

    service.active.set(null);
    api.assign.mockClear();
    service.assignToMe();
    expect(api.assign).not.toHaveBeenCalled();
  });

  it('loads contacts and clears the unread count on read', () => {
    api.contacts.mockReturnValue(of([{ id: 3, fullName: 'Manager', role: 'MANAGER' }]));
    service.loadContacts();
    expect(service.contacts().length).toBe(1);

    service.active.set(conversation());
    service.conversations.set([conversation({ unreadCount: 5 })]);
    service.messages.set([message()]);

    service.markRead();

    expect(service.conversations()[0].unreadCount).toBe(0);
    expect(unread.set).toHaveBeenLastCalledWith(0);
  });

  it('handles incoming message events for active and inactive conversations', () => {
    const active = conversation({ id: 1 });
    service.active.set(active);
    service.messages.set([]);
    service.conversations.set([active]);

    (service as unknown as { handleEvent(event: ChatEvent): void }).handleEvent(
      event({ type: 'MESSAGE_CREATED', message: message({ id: 7 }), conversationId: 1 }),
    );

    expect(service.messages().length).toBe(1);
    expect(service.conversations()[0].lastMessagePreview).toBe('Hello');

    const other = conversation({ id: 2, unreadCount: 0 });
    service.conversations.set([other]);
    const tick = service.refreshTick();

    (service as unknown as { handleEvent(event: ChatEvent): void }).handleEvent(
      event({ type: 'MESSAGE_CREATED', message: message({ id: 8 }), conversationId: 2 }),
    );

    expect(service.conversations()[0].unreadCount).toBe(1);
    expect(service.refreshTick()).toBe(tick + 1);
  });

  it('does not duplicate messages with the same id', () => {
    const existing = message({ id: 7 });
    service.active.set(conversation());
    service.messages.set([existing]);
    service.conversations.set([conversation()]);

    (service as unknown as { handleEvent(event: ChatEvent): void }).handleEvent(
      event({ type: 'MESSAGE_CREATED', message: existing, conversationId: 1 }),
    );

    expect(service.messages().length).toBe(1);
  });

  it('marks own messages as read when another participant reads them', () => {
    me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
    service.active.set(conversation());
    service.messages.set([
      message({ id: 1, sender: { id: 1, fullName: 'N', role: 'CUSTOMER' }, readByOthers: false }),
      message({ id: 2, sender: { id: 1, fullName: 'N', role: 'CUSTOMER' }, readByOthers: false }),
    ]);

    (service as unknown as { handleEvent(event: ChatEvent): void }).handleEvent(
      event({ type: 'MESSAGE_READ', readerId: 2, lastReadMessageId: 1, conversationId: 1 }),
    );

    expect(service.messages()[0].readByOthers).toBe(true);
    expect(service.messages()[1].readByOthers).toBe(false);
  });

  it('shows and then clears a typing indicator', () => {
    vi.useFakeTimers();
    try {
      me.set({ id: 1, fullName: 'N', email: 'n@x.com', role: 'CUSTOMER' });
      service.active.set(conversation());

      (service as unknown as { handleEvent(event: ChatEvent): void }).handleEvent(
        event({ type: 'TYPING', typingUser: 'Support', conversationId: 1 }),
      );
      expect(service.typing()).toBe('Support');

      vi.advanceTimersByTime(4000);
      expect(service.typing()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('bumps the refresh tick on conversation updates', () => {
    const before = service.refreshTick();
    (service as unknown as { handleEvent(event: ChatEvent): void }).handleEvent(
      event({ type: 'CONVERSATION_UPDATED', conversationId: 1 }),
    );
    expect(service.refreshTick()).toBe(before + 1);
  });

  it('does not publish typing without a connected websocket', () => {
    service.active.set(conversation());
    expect(() => service.typingStarted()).not.toThrow();
    expect(() => service.disconnect()).not.toThrow();
  });
});
