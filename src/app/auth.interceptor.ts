import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

function withToken<T>(request: HttpRequest<T>, token: string | null): HttpRequest<T> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}

/**
 * Coloca o access token nas chamadas à API. Se o backend responder 401 (token expirado),
 * renova com o refresh token e repete a requisição uma vez.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.url.startsWith('/api/auth/')) {
    return next(request);
  }

  const auth = inject(AuthService);
  return next(withToken(request, auth.accessToken())).pipe(
    catchError((error) => {
      const unauthorized = error instanceof HttpErrorResponse && error.status === 401;
      if (!unauthorized || !auth.hasRefreshToken()) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(switchMap((token) => next(withToken(request, token))));
    }),
  );
};
