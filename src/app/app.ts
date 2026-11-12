import { Component, inject, signal, VERSION } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from './auth.service';

interface NameResponse {
  result: string;
  /** "sub" do access token que o BFF repassou à API. */
  user: string;
}

@Component({
  imports: [RouterOutlet, DatePipe],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);

  protected readonly title = signal('hello-angular');
  protected readonly version = VERSION.full;

  protected readonly name = signal(this.auth.user() ?? '');
  protected readonly greeting = signal('Hello, World!');
  protected readonly apiCaller = signal('');
  protected readonly error = signal('');

  protected login(): void {
    this.auth.login();
  }

  protected greet(): void {
    const name = this.name().trim();
    if (!name) {
      this.greeting.set('Hello, World!');
      this.error.set('Preencha o nome.');
      return;
    }
    if (!this.auth.user()) {
      this.error.set('Clique em Entrar primeiro.');
      return;
    }

    // O BFF repassa para a API com o access token do usuário. Depois, atualiza a validade do token
    // (que o BFF pode ter acabado de renovar).
    this.http
      .post<NameResponse>('/bff/uppercase', { name })
      .pipe(switchMap((response) => this.auth.refreshUser().pipe(switchMap(async () => response))))
      .subscribe({
        next: (response) => {
          this.error.set('');
          this.greeting.set(`Hello, ${response.result}!`);
          this.apiCaller.set(response.user);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 401) {
            this.auth.clearSession();
            this.error.set('Sessão expirada. Clique em Entrar de novo.');
          } else {
            this.error.set('Não foi possível falar com o backend. Ele e o auth-server estão rodando?');
          }
        },
      });
  }

  protected logout(): void {
    this.auth.logout().subscribe({ error: () => this.auth.clearSession() });
  }
}
