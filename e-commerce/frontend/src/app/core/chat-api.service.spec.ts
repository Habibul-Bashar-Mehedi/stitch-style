import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatApiService } from './chat-api.service';

describe('ChatApiService', () => {
  let api: ChatApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ChatApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('covers customer conversation endpoints', () => {
    api.getConversations().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/me/chat/conversations' }).flush([]);

    api.createSupport({ orderNumber: 'ORD-1', clientMessageId: 'c1', body: 'Hi' }).subscribe();
    const created = http.expectOne({ method: 'POST', url: '/api/v1/me/chat/conversations' });
    expect(created.request.body).toEqual({ orderNumber: 'ORD-1', clientMessageId: 'c1', body: 'Hi' });
    created.flush({});

    api.getConversation(4).subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/me/chat/conversations/4' }).flush({});

    api.getMessages(4, 99).subscribe();
    const messages = http.expectOne((request) => request.url === '/api/v1/me/chat/conversations/4/messages');
    expect(messages.request.params.get('beforeId')).toBe('99');
    messages.flush({ items: [], hasMore: false });

    api.send(4, { clientMessageId: 'c2', body: 'Hello' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/me/chat/conversations/4/messages' }).flush({});

    api.markRead(4, 9).subscribe();
    const read = http.expectOne({ method: 'POST', url: '/api/v1/me/chat/conversations/4/read' });
    expect(read.request.body).toEqual({ lastReadMessageId: 9 });
    read.flush(null);
  });

  it('omits an undefined beforeId parameter', () => {
    api.getMessages(4).subscribe();
    const request = http.expectOne((item) => item.url === '/api/v1/me/chat/conversations/4/messages');
    expect(request.request.params.has('beforeId')).toBe(false);
    request.flush({ items: [], hasMore: false });
  });

  it('covers staff conversation endpoints and filters', () => {
    api.staffConversations({ type: 'SUPPORT', status: '', assigned: undefined, q: 'nusrat' }).subscribe();
    const list = http.expectOne((request) => request.url === '/api/v1/admin/chat/conversations');
    expect(list.request.params.get('type')).toBe('SUPPORT');
    expect(list.request.params.get('q')).toBe('nusrat');
    expect(list.request.params.has('status')).toBe(false);
    expect(list.request.params.has('assigned')).toBe(false);
    list.flush([]);

    api.staffConversation(3).subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/chat/conversations/3' }).flush({});

    api.staffMessages(3, 10).subscribe();
    http.expectOne((request) => request.url === '/api/v1/admin/chat/conversations/3/messages').flush({ items: [], hasMore: false });

    api.staffSend(3, { clientMessageId: 's1', body: 'Reply' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/chat/conversations/3/messages' }).flush({});

    api.staffMarkRead(3, 11).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/chat/conversations/3/read' }).flush(null);

    api.assign(3, 7).subscribe();
    const assigned = http.expectOne({ method: 'PATCH', url: '/api/v1/admin/chat/conversations/3/assign' });
    expect(assigned.request.body).toEqual({ staffUserId: 7 });
    assigned.flush({});

    api.close(3).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/chat/conversations/3/close' }).flush({});

    api.contacts().subscribe();
    http.expectOne({ method: 'GET', url: '/api/v1/admin/chat/contacts' }).flush([]);

    api.createInternal({ recipientUserId: 8, clientMessageId: 'dm', body: 'Hi' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/chat/internal' }).flush({});
  });
});
