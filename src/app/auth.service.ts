import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, tap } from 'rxjs';

interface MeResponse {
  user: string;
  accessTokenExpiresAt: string;
}

/**
 * Padrão BFF: o Angular não vê token nenhum. O login acontece na página do servidor de autorização,
 * e o backend (BFF) guarda os tokens na sessão. O navegador só tem o cookie JSESSIONID (HttpOnly).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly userValue = signal<string | null>(null);
  private readonly tokenExpiresAtValue = signal<Date | null>(null);

  readonly user = this.userValue.asReadonly();
  /** Validade do access token que está guardado no servidor (o token em si nunca chega aqui). */
  readonly tokenExpiresAt = this.tokenExpiresAtValue.asReadonly();

  /** Leva o navegador ao login OAuth 2: BFF -> página de login do auth-server -> volta para o app. */
  login(): void {
    window.location.href = '/oauth2/authorization/hello';
  }

  /** Pergunta ao BFF quem está logado. Usado ao abrir a página (F5) e para atualizar a validade do token. */
  refreshUser(): Observable<void> {
    return this.http.get<MeResponse>('/bff/me').pipe(
      tap((response) => {
        this.userValue.set(response.user);
        this.tokenExpiresAtValue.set(new Date(response.accessTokenExpiresAt));
      }),
      map(() => undefined),
    );
  }

  /** Ao abrir o app: sem sessão, o BFF responde 401 e o app abre deslogado. */
  restoreSession(): Observable<void> {
    return this.refreshUser().pipe(catchError(() => of(undefined)));
  }

  /**
   * Encerra a sessão no BFF e depois navega até o auth-server para encerrar a sessão de lá também
   * (senão o próximo "Entrar" logaria direto, sem pedir o nome).
   */
  logout(): Observable<void> {
    return this.http.post<{ redirectUrl: string }>('/logout', null).pipe(
      tap((response) => (window.location.href = response.redirectUrl)),
      map(() => undefined),
      finalize(() => this.clearSession()),
    );
  }

  clearSession(): void {
    this.userValue.set(null);
    this.tokenExpiresAtValue.set(null);
  }
}
