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

O backend fica em [`backend/`](backend) (Spring Boot 4.1.2, Java 17). Ele expõe um endpoint que recebe um nome e devolve em maiúsculas (só para quem está logado, veja abaixo):

```
POST /api/protegido/uppercase
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

Só para estudo: **sem senha**, basta digitar um nome, mas **só o nome `erick` entra** (maiúsculas e minúsculas não importam). O servidor guarda o nome na sessão HTTP (`HttpSession`, a mesma API de servlet do WildFly) e o navegador fica com o cookie `JSESSIONID`, reenviado em toda chamada.

| Método | Caminho       | O que faz                                                                |
| ------ | ------------- | ------------------------------------------------------------------------ |
| POST   | `/api/login`  | `{ "name": "erick" }` cria a sessão; outro nome dá 401; nome vazio, 400   |
| GET    | `/api/me`     | `{ "name": "erick" }` se houver sessão; senão 401                          |
| POST   | `/api/logout` | invalida a sessão (204)                                                    |

A regra fica em [`SomenteErickAuthenticationProvider`](backend/src/main/java/com/example/backend/SomenteErickAuthenticationProvider.java), com o nome fixo no código (`USUARIO_PERMITIDO`). O controller não decide nada: ele pede ao `AuthenticationManager` do Spring Security para autenticar, e o manager consulta o provider. Se o provider lança `BadCredentialsException`, o controller responde 401.

> **Histórico:** antes, a regra era um `if` no próprio `SessaoController`. Compare os dois com `git show` no commit *Troca o if do login por um AuthenticationProvider*: o comportamento é o mesmo, mas agora quem entra é decidido num componente próprio. Para exigir senha, por exemplo, bastaria trocar o provider, sem mexer no controller.

Dá para testar sem tela, com o `curl` guardando os cookies num arquivo (`-c` grava, `-b` envia). Todo POST precisa do token CSRF (veja [Proteção contra CSRF](#proteção-contra-csrf)): primeiro uma chamada qualquer para receber o cookie `XSRF-TOKEN`, depois o mesmo valor no header `X-XSRF-TOKEN`:

```bash
curl -s -o /dev/null -c cookies.txt http://localhost:8080/api/publico/mensagem
TOKEN=$(grep XSRF-TOKEN cookies.txt | cut -f7)
curl -i -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"name":"erick"}' http://localhost:8080/api/login
curl -i -b cookies.txt http://localhost:8080/api/me
curl -i -b cookies.txt -H "X-XSRF-TOKEN: $TOKEN" -X POST http://localhost:8080/api/logout
curl -i -b cookies.txt http://localhost:8080/api/me
```

A resposta do login traz `Set-Cookie: JSESSIONID=...`; o último `/api/me` dá 401 porque a sessão foi invalidada. Sem o header `X-XSRF-TOKEN`, o login dá 403. Com outro nome no lugar de `erick`, dá 401 e nenhuma sessão é criada.

Na tela, o formulário **Entrar** chama `/api/login` e, depois, a saudação usa `/api/protegido/uppercase` para mostrar o nome em maiúsculas. Como o `ng serve` faz proxy de `/api`, front e API ficam na mesma origem (`localhost:4200`) e o navegador envia o `JSESSIONID` sozinho, sem CORS. Um F5 mantém o login: a tela chama `/api/me` ao abrir.

### Endpoint público × endpoint protegido

| Método | Caminho                     | Acesso                                                   |
| ------ | --------------------------- | -------------------------------------------------------- |
| GET    | `/api/publico/mensagem`     | **público**: `{ "mensagem": ... }`, mesmo sem login        |
| GET    | `/api/protegido/hora-login` | **protegido**: `{ "name", "horaLogin" }`; sem sessão, 401 |
| POST   | `/api/protegido/uppercase`  | **protegido**: nome em maiúsculas; sem sessão, 401         |

Quem decide o acesso é o **Spring Security**, configurado em [`SecurityConfig`](backend/src/main/java/com/example/backend/SecurityConfig.java): `/api/login` e `/api/publico/**` são liberados, e todo o resto exige login (senão 401). A hora do login é gravada na sessão por `/api/login`. A página mostra a mensagem pública sempre e, depois de entrar, a hora do login.

O endpoint de maiúsculas nasceu como `/api/uppercase`, antes de existir login, e depois foi movido para `/api/protegido/uppercase`.

> **Histórico:** antes do Spring Security, a proteção era um `HandlerInterceptor` feito à mão (`SessaoObrigatoriaInterceptor`), que conferia se havia um nome na sessão. Compare os dois com `git show` no commit *Troca o interceptor pelo Spring Security*: o comportamento é o mesmo, mas agora as regras ficam declaradas num lugar só, e o CSRF vem junto.

### Proteção contra CSRF

Como o navegador envia o `JSESSIONID` sozinho, um site malicioso poderia fazer o navegador de um usuário logado disparar um POST para cá. Para impedir isso, o Spring Security grava um token aleatório no cookie `XSRF-TOKEN` e exige o mesmo valor no header `X-XSRF-TOKEN` em todo POST, PUT e DELETE. Um site de outra origem não consegue ler o cookie, então não consegue montar o header.

Do lado do Angular não há código nenhum: o `HttpClient` já lê o cookie `XSRF-TOKEN` e envia o header `X-XSRF-TOKEN` nas chamadas que alteram dados. Sem o header, a resposta é 403.

## Para quem vem de JSF e WildFly

### A diferença principal: onde a tela vive

No **JSF**, o servidor monta o HTML de cada página. Ele guarda o estado da tela (ViewState) e os managed beans, e o navegador só exibe o resultado e envia os formulários.

No **Angular**, a tela inteira é um programa JavaScript que roda no navegador. O servidor não gera HTML nenhum: ele só responde JSON em `/api/...`, como um serviço REST. Consequências:

- **F5 apaga a memória da tela.** No JSF, um `@SessionScoped` continua lá depois do F5 e a página volta preenchida. Aqui a sessão também continua no servidor, mas a tela recomeça do zero e precisa *perguntar* quem está logado. É por isso que ela chama `/api/me` ao abrir.
- **O servidor não sabe qual tela está aberta.** Ele só vê chamadas HTTP soltas. Navegação, validação de formulário e "o que mostrar" ficam no navegador.
- **CSRF não vem de graça.** No JSF o ViewState funciona como um token contra CSRF. Aqui quem faz esse papel é o Spring Security, com o cookie `XSRF-TOKEN` e o header `X-XSRF-TOKEN` (veja [Proteção contra CSRF](#proteção-contra-csrf)).

### O que é igual ao que você já conhece

- **A sessão é a mesma `HttpSession` do servlet,** com o mesmo cookie `JSESSIONID`. O Spring Boot traz um Tomcat embutido, que faz o papel do Undertow do WildFly. O pacote é o mesmo, `jakarta.servlet`.
- **A sessão fica na memória do servidor:** reiniciar o backend desloga todo mundo, como no WildFly sem replicação de sessão.

### Equivalências aproximadas

| Neste projeto                                 | No mundo JSF / Jakarta EE                                       |
| --------------------------------------------- | --------------------------------------------------------------- |
| `@RestController` + `@GetMapping`              | Recurso JAX-RS (`@Path` + `@GET`)                                |
| `HttpServletRequest.getSession()`              | Igual; num managed bean, seria um `@SessionScoped`               |
| `SecurityConfig` (Spring Security)             | `<security-constraint>` no `web.xml` / Jakarta Security          |
| `.permitAll()` / `.authenticated()`            | URL fora / dentro de uma `<security-constraint>`                 |
| `AuthenticationProvider` (quem pode entrar)    | `IdentityStore` do Jakarta Security / security realm do Elytron  |
| `Authentication` recebido no controller        | `request.getUserPrincipal()` / `SecurityContext` do Jakarta      |
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
7. *Endpoint público e endpoint protegido*: barrar quem não tem sessão, com um interceptor feito à mão.
8. *README: guia para quem vem de JSF e WildFly*: este guia.
9. *Protege o endpoint de maiúsculas*: proteger mais um endpoint só mudando o caminho.
10. *Troca o interceptor pelo Spring Security*: o mesmo comportamento feito pelo framework, mais a proteção contra CSRF.
11. *Só o nome erick pode entrar (if no controller)*: um login que pode ser recusado.
12. *Troca o if do login por um AuthenticationProvider*: a mesma regra no lugar que o Spring Security reserva para ela.

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
