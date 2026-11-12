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

Para rodar (porta 8080), em outro terminal:

```bash
cd backend
./mvnw spring-boot:run
```

No Windows (PowerShell ou cmd), use `.\mvnw.cmd spring-boot:run`. Para rodar os testes do backend: `./mvnw test`.

O `ng serve` encaminha as chamadas a `/api` para `http://localhost:8080` via [`proxy.conf.json`](proxy.conf.json), então o front não precisa de CORS. Suba o backend e o `ng serve`; se o backend estiver parado, a tela mostra uma mensagem de erro ao clicar em **Cumprimentar**.

### Login por sessão (JSESSIONID)

Só para estudo: **sem senha**, basta digitar um nome. O servidor guarda o nome na sessão HTTP (`HttpSession`, a mesma API de servlet do WildFly) e o navegador fica com o cookie `JSESSIONID`, reenviado em toda chamada.

| Método | Caminho       | O que faz                                        |
| ------ | ------------- | ------------------------------------------------ |
| POST   | `/api/login`  | `{ "name": "Ana" }` cria a sessão e guarda o nome |
| GET    | `/api/me`     | `{ "name": "Ana" }` se houver sessão; senão 401    |
| POST   | `/api/logout` | invalida a sessão (204)                            |

Dá para testar sem tela, com o `curl` guardando o cookie num arquivo (`-c` grava, `-b` envia):

```bash
curl -i -c cookies.txt -H "Content-Type: application/json" -d '{"name":"Ana"}' http://localhost:8080/api/login
curl -i -b cookies.txt http://localhost:8080/api/me
curl -i -b cookies.txt -X POST http://localhost:8080/api/logout
curl -i -b cookies.txt http://localhost:8080/api/me
```

A resposta do login traz `Set-Cookie: JSESSIONID=...`; o último `/api/me` dá 401 porque a sessão foi invalidada.

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
