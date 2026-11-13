import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the current user and stops the loading state', () => {
    let loaded: unknown;
    auth.load().subscribe((user) => (loaded = user));

    const request = http.expectOne({ method: 'GET', url: '/api/v1/me' });
    request.flush({ id: 1, fullName: 'Nusrat', email: 'n@x.com', role: 'CUSTOMER' });

    expect((loaded as { fullName: string }).fullName).toBe('Nusrat');
    expect(auth.me()?.email).toBe('n@x.com');
    expect(auth.loading()).toBe(false);
    expect(auth.isStaff()).toBe(false);
  });

  it('treats a failed load as a signed-out visitor', () => {
    let loaded: unknown = 'unset';
    auth.load().subscribe((user) => (loaded = user));

    http.expectOne({ method: 'GET', url: '/api/v1/me' })
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });

    expect(loaded).toBeNull();
    expect(auth.me()).toBeNull();
    expect(auth.loading()).toBe(false);
  });

  it('recognises admin and manager roles as staff', () => {
    auth.me.set({ id: 1, fullName: 'A', email: 'a@x.com', role: 'ADMIN' });
    expect(auth.isStaff()).toBe(true);
    auth.me.set({ id: 2, fullName: 'M', email: 'm@x.com', role: 'MANAGER' });
    expect(auth.isStaff()).toBe(true);
    auth.me.set({ id: 3, fullName: 'C', email: 'c@x.com', role: 'CUSTOMER' });
    expect(auth.isStaff()).toBe(false);
  });

  it('clears the user after logout even when the request fails', () => {
    auth.me.set({ id: 1, fullName: 'A', email: 'a@x.com', role: 'CUSTOMER' });

    auth.logout();
    http.expectOne({ method: 'POST', url: '/api/v1/auth/logout' }).flush(null);

    expect(auth.me()).toBeNull();
  });

  it('clears the user when logout returns an error', () => {
    auth.me.set({ id: 1, fullName: 'A', email: 'a@x.com', role: 'CUSTOMER' });

    auth.logout();
    http.expectOne({ method: 'POST', url: '/api/v1/auth/logout' })
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(auth.me()).toBeNull();
  });
});
