import { Component, inject, OnInit, signal, VERSION } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';

interface NameResponse {
  result: string;
}

interface Usuario {
  name: string;
}

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly title = signal('hello-angular');
  protected readonly version = VERSION.full;

  protected readonly name = signal('');
  protected readonly greeting = signal('Hello, World!');
  protected readonly error = signal('');
  /** Nome guardado na sessão do servidor (cookie JSESSIONID); null = não logado. */
  protected readonly usuario = signal<string | null>(null);

  ngOnInit(): void {
    // Após um F5, o navegador reenvia o JSESSIONID e o servidor diz quem está logado.
    this.http.get<Usuario>('/api/me').subscribe({
      next: (u) => this.entrou(u.name),
      error: () => this.usuario.set(null),
    });
  }

  protected login(): void {
    const name = this.name().trim();
    if (!name) {
      this.error.set('Digite um nome.');
      return;
    }
    this.http.post<Usuario>('/api/login', { name }).subscribe({
      next: (u) => {
        this.error.set('');
        this.name.set('');
        this.entrou(u.name);
      },
      error: () => this.error.set('Não foi possível falar com o backend. Ele está rodando?'),
    });
  }

  protected logout(): void {
    const sair = () => {
      this.usuario.set(null);
      this.greeting.set('Hello, World!');
    };
    this.http.post('/api/logout', null).subscribe({ complete: sair, error: sair });
  }

  /** Marca como logado e cumprimenta com o nome em maiúsculas vindo do backend. */
  private entrou(nome: string): void {
    this.usuario.set(nome);
    this.http.post<NameResponse>('/api/uppercase', { name: nome }).subscribe({
      next: (response) => this.greeting.set(`Hello, ${response.result}!`),
      error: () => this.greeting.set(`Hello, ${nome}!`),
    });
  }
}
