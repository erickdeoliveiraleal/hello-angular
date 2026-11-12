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

Ele abre duas janelas: o backend (porta 8080) e o frontend (porta 4200), e o navegador abre sozinho quando o frontend termina de compilar. Para parar, feche as janelas ou use Ctrl+C nelas.

O script também:
- instala as dependências do frontend (`npm install`) se a pasta `node_modules` não existir;
- se o `JAVA_HOME` estiver vazio ou apontar para um JDK que não existe mais, usa o JDK 17+ mais recente do Eclipse Adoptium, só para aquela execução.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Backend (Spring Boot)

O backend fica em [`backend/`](backend) (Spring Boot 4.1.2, Java 17). Ele expõe um endpoint que recebe um nome e devolve em maiúsculas:

```
POST /api/uppercase
{ "name": "joão" }   ->   { "result": "JOÃO" }
```

### Autenticação (sessão no servidor, JSESSIONID)

`/api/uppercase` exige um usuário logado. O login é sem senha: basta um nome preenchido.

| Endpoint | Corpo | Resposta |
|---|---|---|
| `POST /api/auth/login` | `{ "name": "joão" }` | `{ "user": "joão" }` e o cookie de sessão `JSESSIONID` |
| `GET /api/auth/me` | — | `{ "user": "joão" }`, ou `401` se não há sessão |
| `POST /api/auth/logout` | — | `204`, invalida a sessão e apaga o cookie |

- **Onde fica o usuário logado:** na `HttpSession`, **no servidor**. O navegador só tem o ID da sessão, no cookie `JSESSIONID`. Não há token nenhum no front.
- **Cookie `JSESSIONID`:** `HttpOnly` (o JavaScript não lê), `Secure` (só HTTPS; os navegadores aceitam `http://localhost`) e `SameSite=Strict` (não vai em requisições vindas de outros sites). Veja o [`application.properties`](backend/src/main/resources/application.properties).
- **Expiração:** a sessão expira depois de 15 minutos sem uso (`server.servlet.session.timeout`).
- **Logout:** invalida a sessão **no servidor**, então vale na hora. Uma cópia do cookie deixa de funcionar imediatamente.
- **Fixação de sessão:** no login, se já existia uma sessão, ela ganha um ID novo.
- **CSRF:** como o navegador envia o cookie de sessão sozinho, toda requisição que altera algo (POST, PUT, DELETE) precisa do token CSRF. O backend usa `csrf.spa()` do Spring Security: o token vai num cookie `XSRF-TOKEN` (legível pelo JavaScript) e o `HttpClient` do Angular o devolve sozinho no header `X-XSRF-TOKEN`. Um site de terceiros não consegue ler esse cookie nem enviar o header. O [`CsrfCookieFilter`](backend/src/main/java/com/example/backend/auth/CsrfCookieFilter.java) garante que o cookie já venha na primeira resposta.
- **Limitação:** a sessão fica em memória. Reiniciar o backend desloga todo mundo, e com mais de uma instância seria preciso compartilhar as sessões (por exemplo, com Spring Session e Redis).

No front, o campo de nome serve de login. Ao clicar em **Cumprimentar**, o app entra com aquele nome (ou troca de usuário, se o nome mudou) e chama a API. Ao abrir ou recarregar a página (F5), o app chama `GET /api/auth/me`: se a sessão existe, continua logado; senão, recebe 401 (aparece em vermelho no DevTools, mas é esperado) e abre deslogado. O botão **Sair** chama `/api/auth/logout`.

### Outras versões da autenticação (branches)

O mesmo app foi feito de outras formas, cada uma numa branch, para comparar:

| Branch | Como funciona |
|---|---|
| `master` | sessão no servidor (`JSESSIONID`), descrita acima |
| `jwt-manual` | JWT feito à mão: access token JWT em memória + refresh token opaco em cookie `HttpOnly`, com rotação e famílias |
| `caminho-1-bff` | Spring Authorization Server + BFF: login OAuth 2 no servidor de autorização; o backend guarda os tokens na sessão e o navegador só tem o `JSESSIONID` |
| `caminho-2-pkce` | Spring Authorization Server + Angular com Authorization Code e PKCE: o próprio Angular recebe os tokens |
| `caminho-3-grant-nome` | Spring Authorization Server com um *grant type* customizado, que troca o nome digitado por tokens |

Para trocar de versão: `git switch <branch>` (pare os servidores antes e suba de novo depois).

Para rodar (porta 8080), em outro terminal:

```bash
cd backend
./mvnw spring-boot:run
```

No Windows (PowerShell ou cmd), use `.\mvnw.cmd spring-boot:run`. Para rodar os testes do backend: `./mvnw test`.

O `ng serve` encaminha as chamadas a `/api` para `http://localhost:8080` via [`proxy.conf.json`](proxy.conf.json), então o front não precisa de CORS. Suba o backend e o `ng serve`; se o backend estiver parado, a tela mostra uma mensagem de erro ao clicar em **Cumprimentar**.

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
