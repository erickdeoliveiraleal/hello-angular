import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, shareReplay, tap, throwError } from 'rxjs';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/**
 * Guarda os tokens só em memória: recarregar a página exige novo login.
 * O login é sem senha; basta um nome preenchido.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly accessTokenValue = signal<string | null>(null);
  private readonly refreshTokenValue = signal<string | null>(null);
  private readonly userValue = signal<string | null>(null);
  private readonly refreshCount = signal(0);

  readonly accessToken = this.accessTokenValue.asReadonly();
  readonly user = this.userValue.asReadonly();
  /** Quantas vezes o access token foi renovado nesta sessão. */
  readonly refreshes = this.refreshCount.asReadonly();

  private refreshInFlight?: Observable<string>;

  login(name: string): Observable<void> {
    return this.http.post<TokenResponse>('/api/auth/login', { name }).pipe(
      tap((tokens) => {
        this.store(tokens);
        this.userValue.set(name);
        this.refreshCount.set(0);
      }),
      map(() => undefined),
    );
  }

  /** Troca o refresh token por um par novo. Chamadas simultâneas compartilham a mesma requisição. */
  refresh(): Observable<string> {
    const refreshToken = this.refreshTokenValue();
    if (!refreshToken) {
      return throwError(() => new Error('Sem refresh token'));
    }

    this.refreshInFlight ??= this.http
      .post<TokenResponse>('/api/auth/refresh', { refreshToken })
      .pipe(
        tap((tokens) => {
          this.store(tokens);
          this.refreshCount.update((n) => n + 1);
        }),
        map((tokens) => tokens.accessToken),
        catchError((error) => {
          this.logout();
          return throwError(() => error);
        }),
        finalize(() => (this.refreshInFlight = undefined)),
        shareReplay(1),
      );
    return this.refreshInFlight;
  }

  hasRefreshToken(): boolean {
    return this.refreshTokenValue() !== null;
  }

  logout(): void {
    this.accessTokenValue.set(null);
    this.refreshTokenValue.set(null);
    this.userValue.set(null);
  }

  private store(tokens: TokenResponse): void {
    this.accessTokenValue.set(tokens.accessToken);
    this.refreshTokenValue.set(tokens.refreshToken);
  }
}
