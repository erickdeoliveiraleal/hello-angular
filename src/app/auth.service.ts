import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';

interface AccessTokenResponse {
  accessToken: string;
  expiresIn: number;
  user: string;
}

/** Exigido pelo backend nos endpoints de autenticação (proteção contra CSRF). */
const AUTH_HEADERS = new HttpHeaders({ 'X-Requested-With': 'XMLHttpRequest' });

/**
 * O access token fica só em memória. O refresh token fica num cookie HttpOnly que o navegador
 * guarda e envia sozinho para /api/auth; este código nunca o vê.
 * O login é sem senha: basta um nome preenchido.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly accessTokenValue = signal<string | null>(null);
  private readonly userValue = signal<string | null>(null);
  private readonly refreshCount = signal(0);

  readonly accessToken = this.accessTokenValue.asReadonly();
  readonly user = this.userValue.asReadonly();
  /** Quantas vezes o access token foi renovado nesta sessão. */
  readonly refreshes = this.refreshCount.asReadonly();

  private refreshInFlight?: Observable<string>;

  login(name: string): Observable<void> {
    return this.http
      .post<AccessTokenResponse>('/api/auth/login', { name }, { headers: AUTH_HEADERS })
      .pipe(
        tap((response) => {
          this.store(response);
          this.refreshCount.set(0);
        }),
        map(() => undefined),
      );
  }

  /** Pede um access token novo. O cookie com o refresh token vai junto automaticamente. */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.http
      .post<AccessTokenResponse>('/api/auth/refresh', null, { headers: AUTH_HEADERS })
      .pipe(
        tap((response) => {
          this.store(response);
          this.refreshCount.update((n) => n + 1);
        }),
        map((response) => response.accessToken),
        catchError((error) => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => (this.refreshInFlight = undefined)),
        shareReplay(1),
      );
    return this.refreshInFlight;
  }

  /**
   * Chamado ao abrir o app: se o navegador ainda tem o cookie de refresh, recupera a sessão
   * (é o que faz o F5 não deslogar). Sem cookie, o backend responde 401 e o app abre deslogado.
   */
  restoreSession(): Observable<void> {
    return this.refresh().pipe(
      tap(() => this.refreshCount.set(0)),
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  /** Revoga o refresh token no servidor e apaga o cookie. */
  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', null, { headers: AUTH_HEADERS })
      .pipe(finalize(() => this.clearSession()));
  }

  /** Limpa só o estado local (por exemplo, quando o servidor já recusou a sessão). */
  clearSession(): void {
    this.accessTokenValue.set(null);
    this.userValue.set(null);
  }

  private store(response: AccessTokenResponse): void {
    this.accessTokenValue.set(response.accessToken);
    this.userValue.set(response.user);
  }
}
