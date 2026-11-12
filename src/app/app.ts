import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal, VERSION } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';

interface NameResponse {
  result: string;
}

/** Resposta de /api/login, /api/me e /api/posto: quem está logado e o que pode fazer. */
interface Usuario {
  name: string;
  postoTrabalho: string | null;
  postosDisponiveis: string[];
  permissoes: string[];
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
  protected readonly senha = signal('');
  protected readonly greeting = signal('Hello, World!');
  protected readonly error = signal('');
  /** Nome guardado na sessão do servidor (cookie JSESSIONID); null = não logado. */
  protected readonly usuario = signal<string | null>(null);
  /** Vem do endpoint público: aparece mesmo sem login. */
  protected readonly mensagemPublica = signal('');
  /** Vem do endpoint protegido: só com sessão. */
  protected readonly horaLogin = signal<string | null>(null);
  /** Posto de trabalho atual, os postos possíveis e as permissões do posto (vêm do backend). */
  protected readonly posto = signal<string | null>(null);
  protected readonly postos = signal<string[]>([]);
  protected readonly permissoes = signal<string[]>([]);
  /** Só para estudo: mostra também os botões sem permissão, para ver o backend recusar. */
  protected readonly mostrarTudo = signal(false);
  /** Resultado da última ação (registrar visita, editar escala). */
  protected readonly resultado = signal('');

  ngOnInit(): void {
    this.http.get<{ mensagem: string }>('/api/publico/mensagem').subscribe({
      next: (m) => this.mensagemPublica.set(m.mensagem),
      error: () => this.mensagemPublica.set('Backend fora do ar.'),
    });

    // Após um F5, o navegador reenvia o JSESSIONID e o servidor diz quem está logado.
    this.http.get<Usuario>('/api/me').subscribe({
      next: (u) => this.entrou(u),
      error: () => this.usuario.set(null),
    });
  }

  protected login(): void {
    const name = this.name().trim();
    const password = this.senha();
    if (!name || !password) {
      this.error.set('Digite o nome e a senha.');
      return;
    }
    // A senha não fica guardada na tela depois da tentativa, dando certo ou não.
    this.senha.set('');
    this.http.post<Usuario>('/api/login', { name, password }).subscribe({
      next: (u) => {
        this.error.set('');
        this.name.set('');
        this.entrou(u);
      },
      error: (erro: HttpErrorResponse) =>
        this.error.set(
          erro.status === 401
            ? 'Nome ou senha inválidos.'
            : 'Não foi possível falar com o backend. Ele está rodando?',
        ),
    });
  }

  protected logout(): void {
    const sair = () => {
      this.usuario.set(null);
      this.horaLogin.set(null);
      this.greeting.set('Hello, World!');
      this.resultado.set('');
      this.aplicar({ name: '', postoTrabalho: null, postosDisponiveis: [], permissoes: [] });
    };
    this.http.post('/api/logout', null).subscribe({ complete: sair, error: sair });
  }

  /**
   * O equivalente ao rendered="#{...}" do JSF: a tela pergunta se pode mostrar um botão.
   * É só aparência; quem garante a permissão é o backend (@PreAuthorize).
   */
  protected pode(permissao: string): boolean {
    return this.permissoes().includes(permissao);
  }

  /** Troca o posto no servidor; a resposta traz as permissões do novo posto. */
  protected trocarPosto(posto: string): void {
    this.resultado.set('');
    this.http.post<Usuario>('/api/posto', { posto }).subscribe({
      next: (u) => this.aplicar(u),
      error: () => this.resultado.set('Não foi possível trocar de posto.'),
    });
  }

  /** Executa uma ação protegida por permissão e mostra o que o servidor respondeu. */
  protected executar(url: string): void {
    this.http.post<{ mensagem: string }>(url, null).subscribe({
      next: (r) => this.resultado.set(r.mensagem),
      error: (erro: HttpErrorResponse) =>
        this.resultado.set(
          erro.status === 403
            ? 'O servidor recusou (403): o seu posto não tem essa permissão.'
            : 'Não foi possível falar com o backend.',
        ),
    });
  }

  private aplicar(u: Usuario): void {
    this.posto.set(u.postoTrabalho);
    this.postos.set(u.postosDisponiveis);
    this.permissoes.set(u.permissoes);
  }

  /** Marca como logado e cumprimenta com o nome em maiúsculas vindo do backend. */
  private entrou(u: Usuario): void {
    const nome = u.name;
    this.usuario.set(nome);
    this.aplicar(u);
    this.http.post<NameResponse>('/api/protegido/uppercase', { name: nome }).subscribe({
      next: (response) => this.greeting.set(`Hello, ${response.result}!`),
      error: () => this.greeting.set(`Hello, ${nome}!`),
    });
    this.http.get<HoraLogin>('/api/protegido/hora-login').subscribe({
      next: (h) => this.horaLogin.set(h.horaLogin),
      error: () => this.horaLogin.set(null),
    });
  }
}
