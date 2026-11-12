import { Component, inject, OnInit, signal, VERSION } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';

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
  /** Nome guardado na sessão do servidor (cookie JSESSIONID); null = não logado. */
  protected readonly usuario = signal<string | null>(null);
  protected readonly error = signal('');

  ngOnInit(): void {
    // Após um F5, o navegador reenvia o JSESSIONID e o servidor diz quem está logado.
    this.http.get<Usuario>('/api/me').subscribe({
      next: (u) => this.usuario.set(u.name),
      error: () => this.usuario.set(null),
    });
  }

  protected login(): void {
    const name = this.name().trim();
    if (!name) {
      this.error.set('Digite um nome.');
      return;
    }
    this.error.set('');
    this.http.post<Usuario>('/api/login', { name }).subscribe({
      next: (u) => {
        this.usuario.set(u.name);
        this.name.set('');
      },
      error: () => this.error.set('Não foi possível entrar. O backend está rodando?'),
    });
  }

  protected logout(): void {
    this.http.post('/api/logout', null).subscribe({
      complete: () => this.usuario.set(null),
      error: () => this.usuario.set(null),
    });
  }
}
