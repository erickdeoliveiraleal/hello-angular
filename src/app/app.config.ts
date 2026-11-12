import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // A proteção XSRF do HttpClient vem ligada por padrão (cookie XSRF-TOKEN -> header X-XSRF-TOKEN).
    provideHttpClient(),
    // Recupera a sessão do BFF (cookie JSESSIONID) antes de mostrar a tela.
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ]
};
