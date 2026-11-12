package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Testa os cookies numa requisição HTTP de verdade. No MockMvc, o suporte de testes do Spring Security
 * troca o repositório de tokens CSRF e o cookie XSRF-TOKEN não aparece.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CsrfCookieHttpTests {

	@LocalServerPort
	private int port;

	@Test
	void primeiraRequisicaoEntregaOCookieCsrfLegivelESemCriarSessao() throws Exception {
		HttpResponse<Void> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me")).build(),
					HttpResponse.BodyHandlers.discarding());

		assertThat(response.statusCode()).isEqualTo(401);
		List<String> cookies = response.headers().allValues("Set-Cookie");
		assertThat(cookies).anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly"));
		assertThat(cookies).noneSatisfy(cookie -> assertThat(cookie).startsWith("JSESSIONID="));
	}

}
