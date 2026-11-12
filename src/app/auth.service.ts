import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, map, of, tap } from 'rxjs';
import { AUTH_CONFIG } from './auth.config';
import { codeChallengeFor, decodeJwtPayload, randomString } from './pkce';

interface TokenResponse {
  access_token: string;
  id_token: string;
  expires_in: number;
  refresh_token?: string;
}

/** Guardado só durante a ida e volta ao login (sobrevive ao redirecionamento, some ao fechar a aba). */
interface PendingLogin {
  state: string;
  codeVerifier: string;
}

const PENDING_LOGIN_KEY = 'pkce-login';

/**
 * Authorization Code + PKCE feito no próprio Angular (cliente público).
 * Os tokens ficam em memória: um F5 os perde e é preciso clicar em Entrar de novo (o auth-server
 * ainda lembra do usuário, então não pede o nome).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly accessTokenValue = signal<string | null>(null);
  private readonly idTokenValue = signal<string | null>(null);
  private readonly userValue = signal<string | null>(null);
  private readonly expiresAtValue = signal<Date | null>(null);
  private readonly hasRefreshTokenValue = signal(false);

  readonly accessToken = this.accessTokenValue.asReadonly();
  readonly user = this.userValue.asReadonly();
  readonly expiresAt = this.expiresAtValue.asReadonly();
  /** O auth-server não emite refresh token para cliente público; isso aparece na tela. */
  readonly hasRefreshToken = this.hasRefreshTokenValue.asReadonly();

  /** Passo 1: sorteia o code_verifier e vai para o /oauth2/authorize do auth-server. */
  async login(): Promise<void> {
    const pending: PendingLogin = { state: randomString(16), codeVerifier: randomString(32) };
    sessionStorage.setItem(PENDING_LOGIN_KEY, JSON.stringify(pending));

    const params = new HttpParams()
      .set('response_type', 'code')
      .set('client_id', AUTH_CONFIG.clientId)
      .set('redirect_uri', AUTH_CONFIG.redirectUri)
      .set('scope', AUTH_CONFIG.scope)
      // state: protege contra uma "volta do login" que não foi este app que iniciou.
      .set('state', pending.state)
      .set('code_challenge', await codeChallengeFor(pending.codeVerifier))
      .set('code_challenge_method', 'S256');
    window.location.href = `${AUTH_CONFIG.issuer}/oauth2/authorize?${params.toString()}`;
  }

  /**
   * Passo 2, ao abrir o app: se a URL tem ?code=...&state=..., é a volta do login. Confere o state e
   * troca o código por tokens no /oauth2/token, apresentando o code_verifier.
   */
  async handleRedirectCallback(): Promise<void> {
    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    if (!code) {
      return;
    }

    const pending = this.takePendingLogin();
    // Tira ?code e ?state da barra de endereço (um código só pode ser usado uma vez).
    window.history.replaceState(null, '', url.pathname);
    if (!pending || pending.state !== state) {
      console.warn('Volta do login ignorada: state não confere.');
      return;
    }

    const body = new HttpParams()
      .set('grant_type', 'authorization_code')
      .set('client_id', AUTH_CONFIG.clientId)
      .set('code', code)
      .set('redirect_uri', AUTH_CONFIG.redirectUri)
      .set('code_verifier', pending.codeVerifier);

    await firstValueFrom(
      this.http.post<TokenResponse>(`${AUTH_CONFIG.issuer}/oauth2/token`, body).pipe(
        tap((tokens) => this.store(tokens)),
        map(() => undefined),
        catchError((error) => {
          console.error('Falha ao trocar o código por tokens', error);
          return of(undefined);
        }),
      ),
    );
  }

  /** Sai do app e encerra a sessão no auth-server (senão o próximo Entrar não pediria o nome). */
  logout(): void {
    const idToken = this.idTokenValue();
    this.clearSession();
    if (!idToken) {
      return;
    }
    const params = new HttpParams()
      .set('id_token_hint', idToken)
      .set('post_logout_redirect_uri', AUTH_CONFIG.redirectUri);
    window.location.href = `${AUTH_CONFIG.issuer}/connect/logout?${params.toString()}`;
  }

  clearSession(): void {
    this.accessTokenValue.set(null);
    this.userValue.set(null);
    this.expiresAtValue.set(null);
    this.hasRefreshTokenValue.set(false);
  }

  private store(tokens: TokenResponse): void {
    this.accessTokenValue.set(tokens.access_token);
    this.idTokenValue.set(tokens.id_token);
    this.userValue.set(decodeJwtPayload<{ sub: string }>(tokens.id_token).sub);
    this.expiresAtValue.set(new Date(Date.now() + tokens.expires_in * 1000));
    this.hasRefreshTokenValue.set(!!tokens.refresh_token);
  }

  private takePendingLogin(): PendingLogin | null {
    const raw = sessionStorage.getItem(PENDING_LOGIN_KEY);
    sessionStorage.removeItem(PENDING_LOGIN_KEY);
    return raw ? (JSON.parse(raw) as PendingLogin) : null;
  }
}
