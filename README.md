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

### Autenticação (JWT)

`/api/uppercase` exige um access token JWT no header `Authorization: Bearer <token>`. O login é sem senha: basta um nome preenchido.

| Endpoint | Corpo | Resposta |
|---|---|---|
| `POST /api/auth/login` | `{ "name": "joão" }` | `{ "accessToken", "refreshToken", "expiresIn" }` |
| `POST /api/auth/refresh` | `{ "refreshToken": "..." }` | um par novo de tokens |

- **Access token:** vale 1 minuto (curto de propósito, para ver o refresh acontecer). Só serve para chamar a API.
- **Refresh token:** vale 1 dia e só serve para pedir um par novo. Cada refresh token pode ser usado **uma vez**; o backend guarda os já usados em memória, então essa lista zera quando ele reinicia.
- Os tokens são assinados com HS256. A chave vem da variável de ambiente `JWT_SECRET` (Base64, 256 bits); sem ela, é usada uma chave de desenvolvimento que está no `application.properties`. **Em produção, sempre defina `JWT_SECRET`.**
- Os tempos de validade ficam em `app.jwt.access-token-ttl` e `app.jwt.refresh-token-ttl`, no [`application.properties`](backend/src/main/resources/application.properties).

No front, o campo de nome serve de login. Ao clicar em **Cumprimentar**, o app entra com aquele nome (ou troca de usuário, se o nome mudou) e chama a API. O [`auth.interceptor.ts`](src/app/auth.interceptor.ts) coloca o token em cada chamada. Se a API responder 401 porque o token expirou, ele renova com o refresh token e repete a chamada. Os tokens ficam só em memória: recarregar a página exige um novo login.

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
