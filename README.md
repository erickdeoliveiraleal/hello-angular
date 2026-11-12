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

Ele abre três janelas: o servidor de autorização (porta 9000), o backend (porta 8080) e o frontend (porta 4200), e o navegador abre sozinho quando o frontend termina de compilar. Para parar, feche as janelas ou use Ctrl+C nelas.

O script também:
- instala as dependências do frontend (`npm install`) se a pasta `node_modules` não existir;
- se o `JAVA_HOME` estiver vazio ou apontar para um JDK que não existe mais, usa o JDK 17+ mais recente do Eclipse Adoptium, só para aquela execução.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Arquitetura desta branch: grant type customizado (`caminho-3-grant-nome`)

```
Navegador (Angular) — o campo de nome faz o login, como antes
   │  POST /api/auth/login { name }        → access token no JSON + refresh token em cookie HttpOnly
   ▼
backend/ (porta 8080) — cliente confidencial do auth-server
   │  POST /oauth2/token  grant_type=urn:hello:params:oauth:grant-type:name  name=...
   ▼
auth-server/ (porta 9000) — Spring Authorization Server com o grant customizado
```

A tela e o contrato da API são os mesmos da branch `jwt-manual`. A diferença é **quem emite os tokens**: lá, o próprio backend; aqui, o Spring Authorization Server.

### auth-server: o grant customizado

O OAuth 2 permite *grant types* de extensão, identificados por uma URI (RFC 6749, seção 4.5). Este projeto define `urn:hello:params:oauth:grant-type:name`: "troque um nome por tokens".

| Classe | Papel |
|---|---|
| [`NameGrant`](auth-server/src/main/java/com/example/authserver/NameGrant.java) | o identificador do grant e o nome do parâmetro |
| [`NameGrantAuthenticationConverter`](auth-server/src/main/java/com/example/authserver/NameGrantAuthenticationConverter.java) | lê o `POST /oauth2/token`; se o `grant_type` for o de nome, monta o pedido |
| [`NameGrantAuthenticationProvider`](auth-server/src/main/java/com/example/authserver/NameGrantAuthenticationProvider.java) | confere o cliente, gera o access token (JWT, RS256) e o refresh token e grava a autorização |
| [`SecurityConfig`](auth-server/src/main/java/com/example/authserver/SecurityConfig.java) | registra o grant no `/oauth2/token` e o cliente `backend` |

- **Cliente registrado:** o backend (`client_id=backend`, segredo `backend-secret` só para desenvolvimento), com o grant de nome e o `refresh_token`.
- **Refresh e revogação** usam os endpoints padrão (`/oauth2/token` com `grant_type=refresh_token` e `/oauth2/revoke`). O refresh token tem **rotação**: cada uso devolve um novo e invalida o anterior.
- **Sem tela de login:** quem chama o auth-server é o backend, servidor a servidor.

> **Atenção:** este grant é, na prática, o antigo *password grant* sem senha, que o OAuth 2.1 removeu. O auth-server **confia no cliente** para dizer quem é o usuário. Serve para manter o campo de nome do Angular como login; num sistema real, use o fluxo Authorization Code (branches `caminho-1-bff` e `caminho-2-pkce`).

### backend

- [`AuthServerClient`](backend/src/main/java/com/example/backend/auth/AuthServerClient.java): chama o auth-server (grant de nome, refresh, revogação), autenticando-se com `client_id` e segredo.
- [`AuthController`](backend/src/main/java/com/example/backend/auth/AuthController.java): mesmos endpoints da versão `jwt-manual`:

| Endpoint | O que faz |
|---|---|
| `POST /api/auth/login` | `{ "name": "joão" }` → tokens do auth-server; access token no JSON, refresh token no cookie |
| `POST /api/auth/refresh` | usa o cookie; o auth-server gira o refresh token |
| `POST /api/auth/logout` | revoga o refresh token no auth-server e apaga o cookie |

  Os três exigem o header `X-Requested-With: XMLHttpRequest` (proteção CSRF, junto com o `SameSite=Strict`).
- O cookie `refresh_token` é `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`: o JavaScript não lê.
- **API** (`/api/uppercase`): aceita só access tokens JWT do auth-server, validados pela chave pública em `/oauth2/jwks`.

### Angular

O mesmo da versão `jwt-manual`: o campo de nome faz o login, o [`auth.interceptor.ts`](src/app/auth.interceptor.ts) coloca o access token nas chamadas e, num 401, renova pelo cookie e repete a chamada. O F5 restaura a sessão com `/api/auth/refresh`.

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
