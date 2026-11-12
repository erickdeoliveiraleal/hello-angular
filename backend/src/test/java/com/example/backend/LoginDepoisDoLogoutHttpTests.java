package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Faz o mesmo que a tela: entra, sai e entra de novo, sem recarregar a página.
 *
 * Usa o Tomcat de verdade e um "navegador" simples (HttpClient com CookieManager), que
 * guarda os cookies e os reenvia sozinho. Como o Angular, ele lê o cookie XSRF-TOKEN e
 * manda o valor no header X-XSRF-TOKEN em cada POST.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LoginDepoisDoLogoutHttpTests {

	@LocalServerPort
	private int porta;

	private final CookieManager cookies = new CookieManager();

	private final HttpClient navegador = HttpClient.newBuilder().cookieHandler(cookies).build();

	@Test
	void entrarSairEEntrarDeNovoSemRecarregar() throws Exception {
		assertThat(get("/api/publico/mensagem")).isEqualTo(200);

		assertThat(post("/api/login", "{\"name\":\"erick\",\"password\":\"estudo123\"}")).isEqualTo(200);
		assertThat(post("/api/logout", "")).isEqualTo(204);

		// O logout precisa deixar um token CSRF novo, senão o próximo POST leva 403.
		assertThat(tokenCsrf()).isNotEmpty();
		assertThat(post("/api/login", "{\"name\":\"erick\",\"password\":\"estudo123\"}")).isEqualTo(200);
	}

	private int get(String caminho) throws Exception {
		return navegador.send(HttpRequest.newBuilder(uri(caminho)).GET().build(),
				HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private int post(String caminho, String json) throws Exception {
		HttpRequest.Builder requisicao = HttpRequest.newBuilder(uri(caminho))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json));
		String token = tokenCsrf();
		if (!token.isEmpty()) {
			requisicao.header("X-XSRF-TOKEN", token);
		}
		return navegador.send(requisicao.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	/** O valor do cookie XSRF-TOKEN, como o Angular leria; vazio se não houver. */
	private String tokenCsrf() {
		return cookies.getCookieStore().getCookies().stream()
			.filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse("");
	}

	private URI uri(String caminho) {
		return URI.create("http://localhost:" + porta + caminho);
	}

}
