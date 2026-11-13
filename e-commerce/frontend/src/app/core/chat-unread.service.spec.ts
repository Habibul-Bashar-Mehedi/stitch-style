import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatUnreadService } from './chat-unread.service';

describe('ChatUnreadService', () => {
  let service: ChatUnreadService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatUnreadService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sums unread counts from the customer conversations endpoint', () => {
    service.refresh();

    http.expectOne({ method: 'GET', url: '/api/v1/me/chat/conversations' }).flush([
      { id: 1, unreadCount: 2 },
      { id: 2, unreadCount: 3 },
    ]);

    expect(service.count()).toBe(5);
  });

  it('keeps the previous count when the refresh fails', () => {
    service.set(4);

    service.refresh();
    http.expectOne({ method: 'GET', url: '/api/v1/me/chat/conversations' })
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(service.count()).toBe(4);
  });

  it('never stores a negative count', () => {
    service.set(-10);
    expect(service.count()).toBe(0);
    service.set(7);
    expect(service.count()).toBe(7);
  });
});
