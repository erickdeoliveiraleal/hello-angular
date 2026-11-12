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

O `ng serve` encaminha as chamadas a `/api` para `http://localhost:8080` via [`proxy.conf.json`](proxy.conf.json), então o front não precisa de CORS. Suba o backend e o `ng serve`; se o backend estiver parado, a tela mostra uma mensagem de erro ao clicar em **Entrar**.

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

Na tela, o formulário **Entrar** chama `/api/login` e, depois, a saudação usa `/api/uppercase` para mostrar o nome em maiúsculas. Como o `ng serve` faz proxy de `/api`, front e API ficam na mesma origem (`localhost:4200`) e o navegador envia o `JSESSIONID` sozinho, sem CORS. Um F5 mantém o login: a tela chama `/api/me` ao abrir.

### Endpoint público × endpoint protegido

| Método | Caminho                     | Acesso                                                   |
| ------ | --------------------------- | -------------------------------------------------------- |
| GET    | `/api/publico/mensagem`     | **público**: `{ "mensagem": ... }`, mesmo sem login        |
| GET    | `/api/protegido/hora-login` | **protegido**: `{ "name", "horaLogin" }`; sem sessão, 401 |

Tudo em `/api/protegido/**` passa pelo `SessaoObrigatoriaInterceptor`, que devolve 401 se a sessão não tiver um nome. A hora do login é gravada na sessão por `/api/login`. A página mostra a mensagem pública sempre e, depois de entrar, a hora do login.

O `/api/uppercase` continua público de propósito: ele vem de antes do login e fica fora de `/api/protegido/**`. Para protegê-lo, bastaria movê-lo para lá ou incluir o caminho no interceptor.

## Para quem vem de JSF e WildFly

### A diferença principal: onde a tela vive

No **JSF**, o servidor monta o HTML de cada página. Ele guarda o estado da tela (ViewState) e os managed beans, e o navegador só exibe o resultado e envia os formulários.

No **Angular**, a tela inteira é um programa JavaScript que roda no navegador. O servidor não gera HTML nenhum: ele só responde JSON em `/api/...`, como um serviço REST. Consequências:

- **F5 apaga a memória da tela.** No JSF, um `@SessionScoped` continua lá depois do F5 e a página volta preenchida. Aqui a sessão também continua no servidor, mas a tela recomeça do zero e precisa *perguntar* quem está logado. É por isso que ela chama `/api/me` ao abrir.
- **O servidor não sabe qual tela está aberta.** Ele só vê chamadas HTTP soltas. Navegação, validação de formulário e "o que mostrar" ficam no navegador.
- **CSRF não vem de graça.** No JSF o ViewState funciona como um token contra CSRF. Aqui não há nada equivalente: este projeto de estudo não se protege contra isso. Num projeto real, o Spring Security resolve isso com um token próprio.

### O que é igual ao que você já conhece

- **A sessão é a mesma `HttpSession` do servlet,** com o mesmo cookie `JSESSIONID`. O Spring Boot traz um Tomcat embutido, que faz o papel do Undertow do WildFly. O pacote é o mesmo, `jakarta.servlet`.
- **A sessão fica na memória do servidor:** reiniciar o backend desloga todo mundo, como no WildFly sem replicação de sessão.

### Equivalências aproximadas

| Neste projeto                                 | No mundo JSF / Jakarta EE                                       |
| --------------------------------------------- | --------------------------------------------------------------- |
| `@RestController` + `@GetMapping`              | Recurso JAX-RS (`@Path` + `@GET`)                                |
| `HttpServletRequest.getSession()`              | Igual; num managed bean, seria um `@SessionScoped`               |
| `SessaoObrigatoriaInterceptor`                 | Um `Filter` de servlet ou um `ContainerRequestFilter` do JAX-RS  |
| `./mvnw spring-boot:run` (servidor embutido)   | Gerar o `.war` e fazer deploy no WildFly                         |
| `application.properties`                       | `standalone.xml` + `web.xml`                                     |
| Componente Angular (`app.ts` + `app.html`)     | Managed bean + página `.xhtml`                                   |
| `signal(...)` lido no template com `{{ }}`     | Propriedade do bean lida com `#{...}`                            |
| `(submit)="login()"`                          | `action="#{bean.login}"` num `h:commandButton`                   |
| `@if (usuario()) { ... }`                      | `rendered="#{bean.usuario != null}"`                             |
| `HttpClient.post('/api/login', ...)`           | O POST que o JSF faz sozinho ao enviar o formulário              |

### Por que existe o `proxy.conf.json`

Em desenvolvimento, o Angular (`ng serve`, porta 4200) e o Spring (porta 8080) rodam em processos separados. O proxy faz `/api` parecer vir da própria porta 4200. Assim o navegador trata tudo como um único site, envia o `JSESSIONID` normalmente e não esbarra em CORS. No JSF essa questão não existe, porque a tela e o backend saem do mesmo `.war`.

### Ordem sugerida para estudar os commits

```bash
git log --reverse --oneline
git show <hash>
```

1. *Hello World com Angular*: a estrutura de um projeto Angular.
2. *Adiciona campo de texto e botão de cumprimento*: uma tela que reage a eventos, ainda sem servidor.
3. *Adiciona backend Spring Boot com endpoint de maiúsculas*: um endpoint REST.
4. *Frontend chama o backend*: o navegador falando JSON com o servidor.
5. *Backend: login sem senha com sessão HTTP*: a sessão, testada com `curl`.
6. *Frontend: tela de login usando a sessão*: a tela usando o cookie.
7. *Endpoint público e endpoint protegido*: barrar quem não tem sessão.

Próximo passo natural: trocar o interceptor feito à mão pelo **Spring Security**, que também cuida de CSRF.

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
