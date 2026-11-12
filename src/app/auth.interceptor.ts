import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Coloca o access token nas chamadas à API (/api/...). As chamadas ao auth-server não levam token.
 * Não há renovação automática: o cliente público não recebe refresh token. Quando o token expira,
 * a API responde 401 e a tela pede para clicar em Entrar de novo.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthService).accessToken();
  if (!token || !request.url.startsWith('/api/')) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
