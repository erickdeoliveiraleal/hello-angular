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

### Autenticação (JWT)

`/api/uppercase` exige um access token JWT no header `Authorization: Bearer <token>`. O login é sem senha: basta um nome preenchido.

| Endpoint | Corpo | Resposta |
|---|---|---|
| `POST /api/auth/login` | `{ "name": "joão" }` | `{ "accessToken", "expiresIn", "user" }` + cookie `refresh_token` |
| `POST /api/auth/refresh` | (vazio; usa o cookie) | um access token novo + cookie novo |
| `POST /api/auth/logout` | (vazio; usa o cookie) | `204`, revoga o refresh token e apaga o cookie |

Os três exigem o header `X-Requested-With: XMLHttpRequest` (veja CSRF abaixo).

- **Access token:** JWT, vale 1 minuto (curto de propósito, para ver o refresh acontecer). Volta no JSON, fica só em memória no front e serve para chamar a API. O backend o valida só pela assinatura, sem consultar nada.
- **Refresh token:** **não é JWT**. É uma string aleatória de 32 bytes (`SecureRandom`, 43 caracteres em Base64URL), sem conteúdo legível. Vale 1 dia e só serve para pedir um access token novo. **Nunca aparece no JSON:** vai num cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`:
  - `HttpOnly`: o JavaScript da página não consegue ler o cookie, então um XSS não rouba o refresh token.
  - `Secure`: só trafega por HTTPS. Os navegadores tratam `http://localhost` como seguro, então funciona no `ng serve`.
  - `SameSite=Strict`: não é enviado em requisições que partem de outro site.
  - `Path=/api/auth`: só vai para os endpoints de autenticação, nunca para o resto da API.
- **Lista de permitidos:** o servidor guarda cada refresh token emitido, mas **só o hash SHA-256** (se a "tabela" vazar, os tokens não podem ser usados). Só vale o refresh token que está guardado, não foi usado, não foi revogado e não expirou. O armazenamento fica atrás da interface [`RefreshTokenStore`](backend/src/main/java/com/example/backend/auth/RefreshTokenStore.java); a implementação atual é em memória, então reiniciar o backend desloga todo mundo. Trocar por banco ou Redis é criar outra implementação.
- **Rotação com famílias:** cada login abre uma *família*. Cada refresh marca o token atual como usado e devolve um novo da mesma família. Se um token **já usado** aparecer de novo, é sinal de roubo (o cliente legítimo já tinha trocado por outro): a **família inteira é revogada**, e o ladrão e o usuário precisam fazer login de novo. O logout e um novo login também revogam a família.
- **Limpeza:** de tempos em tempos (`app.jwt.refresh-cleanup-interval`, 10 minutos), os refresh tokens expirados são apagados da memória.
- **CSRF:** como o navegador envia o cookie sozinho, outro site poderia tentar disparar `/api/auth/refresh`. Além do `SameSite=Strict`, o backend exige o header `X-Requested-With`, que um site de terceiros não consegue enviar sem passar pelo CORS.
- O access token é assinado com HS256. A chave vem da variável de ambiente `JWT_SECRET` (Base64, 256 bits); sem ela, é usada uma chave de desenvolvimento que está no `application.properties`. **Em produção, sempre defina `JWT_SECRET`.**
- Os tempos de validade ficam em `app.jwt.access-token-ttl` e `app.jwt.refresh-token-ttl`, no [`application.properties`](backend/src/main/resources/application.properties).

No front, o campo de nome serve de login. Ao clicar em **Cumprimentar**, o app entra com aquele nome (ou troca de usuário, se o nome mudou) e chama a API. O [`auth.interceptor.ts`](src/app/auth.interceptor.ts) coloca o access token em cada chamada. Se a API responder 401 porque o token expirou, ele chama `/api/auth/refresh` (o cookie vai junto) e repete a chamada.

Ao abrir ou recarregar a página (F5), o app chama `/api/auth/refresh`. Se o cookie ainda for válido, a sessão volta sem novo login. Sem cookie, essa chamada devolve 401 (aparece em vermelho no DevTools, mas é esperado) e o app abre deslogado. O botão **Sair** chama `/api/auth/logout`.

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
