import { Component, signal, VERSION } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('hello-angular');
  protected readonly version = VERSION.full;

  protected readonly name = signal('');
  protected readonly greeting = signal('Hello, World!');

  protected greet(): void {
    const name = this.name().trim();
    this.greeting.set(`Hello, ${name || 'World'}!`);
  }
}
