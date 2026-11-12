import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal, VERSION } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';

interface Usuario {
  name: string;
}

interface HoraLogin {
  name: string;
  horaLogin: string;
}

@Component({
  imports: [DatePipe, RouterOutlet],
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
  /** Vem do endpoint público: aparece mesmo sem login. */
  protected readonly mensagemPublica = signal('');
  /** Vem do endpoint protegido: só com sessão. */
  protected readonly horaLogin = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<{ mensagem: string }>('/api/publico/mensagem').subscribe({
      next: (m) => this.mensagemPublica.set(m.mensagem),
      error: () => this.mensagemPublica.set('Backend fora do ar.'),
    });

    // Após um F5, o navegador reenvia o JSESSIONID e o servidor diz quem está logado.
    this.http.get<Usuario>('/api/me').subscribe({
      next: (u) => this.entrou(u.name),
      error: () => this.usuario.set(null),
    });
  }

  private entrou(nome: string): void {
    this.usuario.set(nome);
    this.http.get<HoraLogin>('/api/protegido/hora-login').subscribe({
      next: (h) => this.horaLogin.set(h.horaLogin),
      error: () => this.horaLogin.set(null),
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
        this.entrou(u.name);
        this.name.set('');
      },
      error: () => this.error.set('Não foi possível entrar. O backend está rodando?'),
    });
  }

  protected logout(): void {
    const sair = () => {
      this.usuario.set(null);
      this.horaLogin.set(null);
    };
    this.http.post('/api/logout', null).subscribe({ complete: sair, error: sair });
  }
}
