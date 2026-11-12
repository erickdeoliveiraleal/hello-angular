import { Component, inject, signal, VERSION } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';
import { AuthService } from './auth.service';

interface NameResponse {
  result: string;
}

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);

  protected readonly title = signal('hello-angular');
  protected readonly version = VERSION.full;

  protected readonly name = signal('');
  protected readonly greeting = signal('Hello, World!');
  protected readonly error = signal('');

  protected greet(): void {
    const name = this.name().trim();
    if (!name) {
      this.greeting.set('Hello, World!');
      this.error.set('Preencha o nome para entrar.');
      return;
    }

    this.ensureLoggedInAs(name)
      .pipe(switchMap(() => this.http.post<NameResponse>('/api/uppercase', { name })))
      .subscribe({
        next: (response) => {
          this.error.set('');
          this.greeting.set(`Hello, ${response.result}!`);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 401) {
            this.auth.logout();
            this.error.set('Sessão expirada. Clique em Cumprimentar para entrar de novo.');
          } else {
            this.error.set('Não foi possível falar com o backend. Ele está rodando?');
          }
        },
      });
  }

  /** O próprio campo de nome serve de login: entra (ou troca de usuário) quando o nome muda. */
  private ensureLoggedInAs(name: string): Observable<void> {
    return this.auth.user() === name ? of(undefined) : this.auth.login(name);
  }
}
