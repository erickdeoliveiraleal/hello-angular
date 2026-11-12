package com.example.backend;

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
 * Sobe o Tomcat de verdade, numa porta livre qualquer, e faz chamadas HTTP reais.
 *
 * Aqui não dá para usar MockMvc: com ele a requisição é simulada, e uma sessão aparece
 * mesmo quando o servidor real não cria nenhuma. O jeito confiável é olhar o cabeçalho
 * Set-Cookie que o Tomcat devolve.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessaoAnonimaHttpTests {

	@LocalServerPort
	private int porta;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void acessoSemLoginNaoCriaSessao() throws Exception {
		HttpResponse<Void> resposta = http.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/me")).GET().build(),
				HttpResponse.BodyHandlers.discarding());

		List<String> cookies = resposta.headers().allValues("Set-Cookie");
		assertThat(resposta.statusCode()).isEqualTo(401);
		assertThat(cookies).anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN="));
		assertThat(cookies).noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
	}

}
