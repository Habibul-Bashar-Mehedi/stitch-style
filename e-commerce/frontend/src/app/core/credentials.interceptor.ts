import { HttpInterceptorFn } from '@angular/common/http';

export const credentialsInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.url.startsWith('/')) {
    return next(request.clone({ withCredentials: true }));
  }
  return next(request);
};
