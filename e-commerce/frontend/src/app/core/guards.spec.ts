import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { staffGuard } from './guards';

describe('staffGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const tree = {} as UrlTree;
  let auth: { isStaff: ReturnType<typeof vi.fn>; load: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { isStaff: vi.fn(), load: vi.fn() };
    router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows staff immediately', () => {
    auth.isStaff.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => staffGuard(route, state));
    expect(result).toBe(true);
    expect(auth.load).not.toHaveBeenCalled();
  });

  it('waits for the session then allows a staff user', () => {
    auth.isStaff.mockReturnValueOnce(false).mockReturnValueOnce(true);
    auth.load.mockReturnValue(of({ role: 'ADMIN' }));

    const result = TestBed.runInInjectionContext(() => staffGuard(route, state)) as unknown as {
      subscribe: (fn: (value: unknown) => void) => void;
    };

    let value: unknown;
    result.subscribe((v) => (value = v));
    expect(value).toBe(true);
  });

  it('redirects a signed-in customer to the storefront', () => {
    auth.isStaff.mockReturnValue(false);
    auth.load.mockReturnValue(of({ role: 'CUSTOMER' }));

    const result = TestBed.runInInjectionContext(() => staffGuard(route, state)) as unknown as {
      subscribe: (fn: (value: unknown) => void) => void;
    };

    let value: unknown;
    result.subscribe((v) => (value = v));
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
    expect(value).toBe(tree);
  });
});
