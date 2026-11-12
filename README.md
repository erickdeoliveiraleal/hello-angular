# HelloAngular

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.5.

## Pré-requisitos

- Node.js `^22.22.3 || ^24.15.0 || >=26.0.0`
- Java 17 ou superior, com `JAVA_HOME` apontando para o JDK (o Maven vem junto no projeto, via `mvnw`)
- Angular CLI 22.2.5 instalado globalmente:

```bash
npm install -g @angular/cli@22.2.5
```

Confira a instalação (rode dentro da pasta do projeto para ver também a versão do `@angular/core`):

```bash
ng version
```

Depois de clonar, instale as dependências do projeto:

```bash
npm install
```

> Sem o CLI global, use `npx ng <comando>` (por exemplo `npx ng serve`) ou `npm start`. Os dois usam o CLI local do projeto.
>
> No PowerShell, se `ng` der erro de política de execução de scripts, use `ng.cmd` ou `npx ng`.

## Subir tudo de uma vez (Windows)

Dê dois cliques em [`iniciar.bat`](iniciar.bat), ou rode no terminal:

```bash
iniciar.bat
```

Ele abre três janelas: o servidor de autorização (porta 9000), o backend (porta 8080) e o frontend (porta 4200). O navegador abre sozinho em **http://127.0.0.1:4200** quando o frontend termina de compilar. Para parar, feche as janelas ou use Ctrl+C nelas.

O script também:
- instala as dependências do frontend (`npm install`) se a pasta `node_modules` não existir;
- se o `JAVA_HOME` estiver vazio ou apontar para um JDK que não existe mais, usa o JDK 17+ mais recente do Eclipse Adoptium, só para aquela execução.

> Use **127.0.0.1**, não `localhost`: o Spring Authorization Server não aceita `localhost` no `redirect_uri`, então o app é registrado em `http://127.0.0.1:4200/`. O `angular.json` já faz o `ng serve` escutar nesse endereço.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://127.0.0.1:4200/`. The application will automatically reload whenever you modify any of the source files.

## Arquitetura desta branch: Authorization Code + PKCE no Angular (`caminho-2-pkce`)

```
Navegador (Angular, 127.0.0.1:4200) — é o próprio cliente OAuth 2 (cliente público)
   │  1. Entrar → /oauth2/authorize (com code_challenge)     → página de login do auth-server
   │  2. volta com ?code=...  → POST /oauth2/token (com code_verifier) direto do navegador (CORS)
   │  3. guarda access token e ID token em memória
   │
   ├──► auth-server/ (porta 9000) — Spring Authorization Server
   └──► backend/ (porta 8080) — API: só aceita "Authorization: Bearer <JWT>"
```

### auth-server (Spring Authorization Server)

Fica em [`auth-server/`](auth-server):
- **Página de login própria, só com o campo de nome** ([`LoginController`](auth-server/src/main/java/com/example/authserver/LoginController.java), [`NameOnlyAuthenticationProvider`](auth-server/src/main/java/com/example/authserver/NameOnlyAuthenticationProvider.java)).
- **Cliente registrado:** o próprio Angular (`client_id=angular`), um **cliente público**: o código roda no navegador, então não tem como guardar um segredo. No lugar do segredo, o **PKCE é obrigatório**.
- **Sem refresh token:** o Spring Authorization Server não emite refresh token para clientes públicos. O access token (JWT, RS256) vale 1 minuto.
- **CORS:** libera `http://127.0.0.1:4200`, porque o Angular chama o `/oauth2/token` direto do navegador. Outras origens são recusadas.
- **OpenID Connect:** ID token, `/userinfo`, logout (`/connect/logout`) e `/.well-known/openid-configuration`.

### backend: só API

`/api/uppercase` aceita apenas um access token JWT do auth-server (validado pela chave pública em `/oauth2/jwks`). Não tem login, sessão, cookie nem CSRF.

```
POST /api/uppercase   Authorization: Bearer <JWT>
{ "name": "maria" }   ->   { "result": "MARIA", "user": "joana" }
```

### Angular

- [`pkce.ts`](src/app/pkce.ts): sorteia o `code_verifier` e calcula o `code_challenge` (SHA-256, Base64URL), com a Web Crypto API do navegador.
- [`auth.service.ts`](src/app/auth.service.ts):
  1. **Entrar:** guarda `state` e `code_verifier` no `sessionStorage` (sobrevivem ao redirecionamento) e vai para o `/oauth2/authorize`.
  2. **Volta do login:** ao abrir com `?code=...`, confere o `state`, troca o código por tokens no `/oauth2/token` apresentando o `code_verifier`, e limpa a URL.
  3. **Sair:** apaga os tokens e vai ao `/connect/logout` do auth-server.
- [`auth.interceptor.ts`](src/app/auth.interceptor.ts): coloca o `Authorization: Bearer` nas chamadas a `/api/`.
- Os tokens ficam **em memória**: um F5 os perde. Como o auth-server ainda lembra do usuário (cookie de sessão dele), o próximo **Entrar** pega tokens novos sem pedir o nome.
- **Token expirado:** sem refresh token, a API responde 401 e a tela pede para clicar em **Entrar** de novo (de novo sem pedir o nome).

### Comparando com o caminho 1 (BFF)

| | Caminho 1 (BFF) | Caminho 2 (PKCE no Angular) |
|---|---|---|
| Quem é o cliente OAuth | o backend (confidencial, com segredo) | o Angular (público, sem segredo) |
| Onde ficam os tokens | na sessão do servidor | na memória do navegador |
| Um XSS consegue roubar tokens | não | sim (o access token) |
| Refresh token | sim, usado pelo BFF | não |
| F5 | continua logado | precisa clicar em Entrar (sem digitar o nome) |
| Backend precisa de sessão e CSRF | sim | não |

As boas práticas atuais para aplicações no navegador (o documento "OAuth 2.0 for Browser-Based Applications", da IETF) preferem o BFF justamente por não deixar token nenhum no JavaScript.

### Detalhe: tolerância de relógio

A API aceita um JWT até **60 segundos depois** do `exp` (padrão do Spring, para absorver diferença de relógio entre servidores). Por isso o access token "de 1 minuto" é recusado só depois de uns 2 minutos.

### Rodar cada parte separadamente

Em três terminais:

```bash
cd auth-server
./mvnw spring-boot:run
```

```bash
cd backend
./mvnw spring-boot:run
```

```bash
ng serve
```

No Windows, use `.\mvnw.cmd spring-boot:run`. Para os testes: `./mvnw test` em `auth-server/` e em `backend/`.

## Outras versões da autenticação (branches)

O mesmo app foi feito de outras formas, cada uma numa branch, para comparar:

| Branch | Como funciona |
|---|---|
| `master` | sessão no servidor (`JSESSIONID`), sem OAuth |
| `jwt-manual` | JWT feito à mão: access token JWT em memória + refresh token opaco em cookie `HttpOnly`, com rotação e famílias |
| `caminho-1-bff` | Spring Authorization Server + BFF: login OAuth 2 no servidor de autorização; o backend guarda os tokens na sessão e o navegador só tem o `JSESSIONID` |
| `caminho-2-pkce` | Spring Authorization Server + Angular com Authorization Code e PKCE: o próprio Angular recebe os tokens |
| `caminho-3-grant-nome` | Spring Authorization Server com um *grant type* customizado, que troca o nome digitado por tokens |

Para trocar de versão: `git switch <branch>` (pare os servidores antes e suba de novo depois).

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
