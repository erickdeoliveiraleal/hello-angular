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

> Use **127.0.0.1**, não `localhost`: o Spring Authorization Server não aceita `localhost` no `redirect_uri`, então o app é registrado em `http://127.0.0.1:4200`. O `angular.json` já faz o `ng serve` escutar nesse endereço.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://127.0.0.1:4200/`. The application will automatically reload whenever you modify any of the source files.

## Arquitetura desta branch: Spring Authorization Server + BFF (`caminho-1-bff`)

```
Navegador (Angular, 127.0.0.1:4200)
   │  cookie JSESSIONID (HttpOnly) — nenhum token chega ao navegador
   ▼
backend/  (porta 8080)
   ├─ BFF  /bff/**, /oauth2/**, /login/**, /logout   → login OAuth 2 e sessão; guarda os tokens
   └─ API  /api/**                                   → só aceita "Authorization: Bearer <JWT>"
   │  Authorization: Bearer <access token do usuário>
   ▼
auth-server/  (porta 9000) — Spring Authorization Server: login, emissão e renovação dos tokens
```

### auth-server (Spring Authorization Server)

Fica em [`auth-server/`](auth-server). É o "provedor de login", no papel que teriam o Keycloak ou o gov.br:
- **Página de login própria, só com o campo de nome** ([`LoginController`](auth-server/src/main/java/com/example/authserver/LoginController.java)). O [`NameOnlyAuthenticationProvider`](auth-server/src/main/java/com/example/authserver/NameOnlyAuthenticationProvider.java) aceita qualquer nome preenchido; num sistema real, aqui entraria a senha.
- **Cliente registrado:** o BFF (`client_id=bff`, segredo `bff-secret` só para desenvolvimento), com Authorization Code + PKCE e refresh token.
- **Tokens:** access token JWT assinado com RSA (RS256), válido por 1 minuto; refresh token válido por 1 dia, com **rotação** (cada uso devolve um novo e invalida o anterior). A chave pública fica em `http://localhost:9000/oauth2/jwks`.
- **OpenID Connect:** ID token, `/userinfo`, logout (`/connect/logout`) e a configuração em `http://localhost:9000/.well-known/openid-configuration`.
- A chave RSA e os tokens ficam em memória: reiniciar o auth-server invalida as sessões.

### backend: BFF + API

**BFF** (o que o navegador acessa):

| Endpoint | O que faz |
|---|---|
| `GET /oauth2/authorization/hello` | começa o login: redireciona para a página de login do auth-server |
| `GET /login/oauth2/code/hello` | volta do login: troca o código pelos tokens e cria a sessão |
| `GET /bff/me` | quem está logado e até quando vale o access token guardado (`401` se não há sessão) |
| `POST /bff/uppercase` | repassa para a API com o access token do usuário |
| `POST /logout` | encerra a sessão e devolve o endereço de logout do auth-server |

- Os tokens ficam **na sessão do servidor**. O navegador só recebe o cookie `JSESSIONID` (`HttpOnly`, `Secure`, `SameSite=Lax`). É `Lax` e não `Strict` porque a volta do login é um redirecionamento vindo de outro site (`localhost:9000`).
- **Renovação automática:** quando o access token está para expirar, o BFF usa o refresh token sozinho ([`ApiClient`](backend/src/main/java/com/example/backend/bff/ApiClient.java) + `OAuth2AuthorizedClientManager`). Na tela, a validade do token muda sem novo login.
- **ID token renovado:** na renovação, o auth-server também emite um ID token novo. O `OidcAuthorizedClientRefreshedEventListener` atualiza o usuário logado com ele, e o [`SaveRefreshedOidcUserListener`](backend/src/main/java/com/example/backend/auth/SaveRefreshedOidcUserListener.java) grava isso na sessão. Sem isso, o logout no auth-server falharia.
- **Logout completo:** encerra a sessão do BFF **e** a do auth-server (OpenID Connect RP-Initiated Logout). Senão, o próximo **Entrar** logaria direto, sem pedir o nome.
- **CSRF:** `csrf.spa()`; o Angular devolve o cookie `XSRF-TOKEN` no header `X-XSRF-TOKEN`.

**API** (`/api/uppercase`): sem sessão e sem cookie. Só aceita um access token JWT do auth-server, validado pela chave pública (`/oauth2/jwks`). Devolve o resultado e o `sub` do token, para mostrar de quem era o token que o BFF repassou:

```
POST /api/uppercase   Authorization: Bearer <JWT>
{ "name": "maria" }   ->   { "result": "MARIA", "user": "joão" }
```

### Angular

- **Entrar** leva à página de login do auth-server (`/oauth2/authorization/hello`); depois do login, o navegador volta para o app já logado.
- **Cumprimentar** chama `/bff/uppercase`. A tela mostra até quando vale o access token guardado no BFF e de quem era o token que a API recebeu.
- O Angular **não tem token nenhum**: nem interceptor, nem refresh. Ao abrir a página (F5), chama `/bff/me` para saber se há sessão.
- O [`proxy.conf.json`](proxy.conf.json) encaminha `/bff`, `/oauth2`, `/login` e `/logout` para o backend, então tudo fica na mesma origem (`127.0.0.1:4200`).

### Rodar cada parte separadamente

Em três terminais, nesta ordem:

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
