/** Onde fica o servidor de autorização e como o Angular está registrado nele. */
export const AUTH_CONFIG = {
  /** Endereço do auth-server (Spring Authorization Server). */
  issuer: 'http://localhost:9000',
  /** Cliente público registrado no auth-server: sem segredo, com PKCE obrigatório. */
  clientId: 'angular',
  /** Para onde o auth-server devolve o usuário com o código. Precisa ser exatamente o registrado. */
  redirectUri: 'http://127.0.0.1:4200/',
  scope: 'openid profile',
} as const;
