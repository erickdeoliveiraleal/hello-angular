import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, tap } from 'rxjs';

interface UserResponse {
  user: string;
}

/**
 * Autenticação por sessão no servidor. O front não guarda token nenhum: o navegador manda o cookie
 * JSESSIONID (HttpOnly) sozinho. A proteção contra CSRF também é automática: o HttpClient do Angular
 * lê o cookie XSRF-TOKEN e o devolve no header X-XSRF-TOKEN em POST, PUT e DELETE.
 * O login é sem senha: basta um nome preenchido.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly userValue = signal<string | null>(null);
  readonly user = this.userValue.asReadonly();

  login(name: string): Observable<void> {
    return this.http.post<UserResponse>('/api/auth/login', { name }).pipe(
      tap((response) => this.userValue.set(response.user)),
      map(() => undefined),
    );
  }

  /**
   * Chamado ao abrir o app: pergunta ao servidor se a sessão ainda existe (é o que faz o F5 não deslogar).
   * Sem sessão, o backend responde 401 e o app abre deslogado. Essa primeira resposta também traz o
   * cookie XSRF-TOKEN, necessário para o login.
   */
  restoreSession(): Observable<void> {
    return this.http.get<UserResponse>('/api/auth/me').pipe(
      tap((response) => this.userValue.set(response.user)),
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  /** Invalida a sessão no servidor e apaga o cookie. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(finalize(() => this.clearSession()));
  }

  /** Limpa só o estado local (por exemplo, quando o servidor já respondeu que a sessão expirou). */
  clearSession(): void {
    this.userValue.set(null);
  }
}
