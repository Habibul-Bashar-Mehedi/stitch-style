import { inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of, tap } from 'rxjs';
import { ApiService } from './api.service';
import { Me } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);

  readonly me = signal<Me | null>(null);
  readonly loading = signal(true);

  load(): Observable<Me | null> {
    return this.api.getMe().pipe(
      tap((user) => {
        this.me.set(user);
        this.loading.set(false);
      }),
      catchError(() => {
        this.me.set(null);
        this.loading.set(false);
        return of(null);
      }),
    );
  }

  login(): void {
    if (typeof window === 'undefined') return;
    const returnUrl = `${window.location.pathname}${window.location.search}`;
    window.location.href = `/oauth2/authorization/google?returnUrl=${encodeURIComponent(returnUrl)}`;
  }

  logout(): void {
    this.api.logout().subscribe({
      next: () => this.me.set(null),
      error: () => this.me.set(null),
    });
  }

  isStaff(): boolean {
    const role = this.me()?.role;
    return role === 'ADMIN' || role === 'MANAGER';
  }
}
