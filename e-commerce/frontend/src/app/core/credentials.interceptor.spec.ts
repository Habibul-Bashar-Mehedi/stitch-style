import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { credentialsInterceptor } from './credentials.interceptor';

describe('credentialsInterceptor', () => {
  function run(url: string): HttpRequest<unknown> {
    let captured: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (request) => {
      captured = request;
      return of(new HttpResponse({ status: 200 }));
    };
    credentialsInterceptor(new HttpRequest('GET', url), next).subscribe();
    if (!captured) throw new Error('interceptor did not forward the request');
    return captured;
  }

  it('sends credentials for same-origin application requests', () => {
    expect(run('/api/v1/me').withCredentials).toBe(true);
  });

  it('leaves external requests untouched', () => {
    expect(run('https://accounts.google.com/.well-known/openid-configuration').withCredentials).toBe(false);
  });
});
