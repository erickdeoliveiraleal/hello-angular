import { Component, inject, signal, VERSION } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';

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

  protected readonly title = signal('hello-angular');
  protected readonly version = VERSION.full;

  protected readonly name = signal('');
  protected readonly greeting = signal('Hello, World!');
  protected readonly error = signal('');

  protected greet(): void {
    const name = this.name().trim();
    if (!name) {
      this.error.set('');
      this.greeting.set('Hello, World!');
      return;
    }

    this.http.post<NameResponse>('/api/uppercase', { name }).subscribe({
      next: (response) => {
        this.error.set('');
        this.greeting.set(`Hello, ${response.result}!`);
      },
      error: () => this.error.set('Não foi possível falar com o backend. Ele está rodando?'),
    });
  }
}
