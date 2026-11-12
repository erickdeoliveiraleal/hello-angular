package com.example.backend.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.auth.AuthServerClient.Tokens;

import jakarta.servlet.http.Cookie;

/**
 * Testa o backend sem o auth-server de verdade: o {@link AuthServerClient} é simulado. O fluxo com o
 * auth-server está nos testes do próprio auth-server e no teste no navegador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

	private static final String COOKIE = AuthController.REFRESH_COOKIE;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthServerClient authServer;

	/** Lê o "sub" dos tokens simulados (no real, valida a assinatura com a chave do auth-server). */
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void loginDevolveAccessTokenNoJsonERefreshTokenEmCookieProtegido() throws Exception {
		given(authServer.tokensForName("joão")).willReturn(new Tokens("access-1", "refresh-1", 60));
		givenSubject("access-1", "joão");

		login("joão", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access-1"))
			.andExpect(jsonPath("$.expiresIn").value(60))
			.andExpect(jsonPath("$.user").value("joão"))
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(cookie().value(COOKIE, "refresh-1"))
			.andExpect(cookie().httpOnly(COOKIE, true))
			.andExpect(cookie().secure(COOKIE, true))
			.andExpect(cookie().sameSite(COOKIE, "Strict"))
			.andExpect(cookie().path(COOKIE, "/api/auth"));
	}

	@Test
	void loginComNomeVazioDevolve400SemChamarOAuthServer() throws Exception {
		login("   ", null).andExpect(status().isBadRequest());
		then(authServer).shouldHaveNoInteractions();
	}

	@Test
	void semHeaderAntiCsrfDevolve403() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"joão\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void novoLoginRevogaORefreshTokenAnterior() throws Exception {
		given(authServer.tokensForName("maria")).willReturn(new Tokens("access-2", "refresh-2", 60));
		givenSubject("access-2", "maria");

		login("maria", "refresh-antigo").andExpect(status().isOk());

		then(authServer).should().revoke("refresh-antigo");
	}

	@Test
	void refreshPeloCookieDevolveTokensNovos() throws Exception {
		given(authServer.refresh("refresh-1")).willReturn(new Tokens("access-2", "refresh-2", 60));
		givenSubject("access-2", "joão");

		refresh("refresh-1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access-2"))
			.andExpect(jsonPath("$.user").value("joão"))
			.andExpect(cookie().value(COOKIE, "refresh-2"));
	}

	@Test
	void refreshRecusadoPeloAuthServerDevolve401() throws Exception {
		given(authServer.refresh(anyString()))
			.willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "recusado"));

		refresh("refresh-usado").andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevogaNoAuthServerEApagaOCookie() throws Exception {
		mockMvc.perform(post("/api/auth/logout")
				.header(AuthController.CSRF_HEADER, AuthController.CSRF_HEADER_VALUE)
				.cookie(new Cookie(COOKIE, "refresh-1")))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge(COOKIE, 0));

		then(authServer).should().revoke("refresh-1");
	}

	@Test
	void apiSemTokenDevolve401EComTokenDevolveMaiusculas() throws Exception {
		mockMvc.perform(post("/api/uppercase").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"joão\"}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/uppercase")
				.with(jwt().jwt(token -> token.subject("joão")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("MARIA"))
			.andExpect(jsonPath("$.user").value("joão"));
	}

	private void givenSubject(String accessToken, String subject) {
		given(jwtDecoder.decode(accessToken)).willReturn(Jwt.withTokenValue(accessToken)
			.header("alg", "RS256")
			.subject(subject)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.build());
	}

	private ResultActions login(String name, String currentRefreshToken) throws Exception {
		var request = post("/api/auth/login")
			.header(AuthController.CSRF_HEADER, AuthController.CSRF_HEADER_VALUE)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\"}");
		if (currentRefreshToken != null) {
			request.cookie(new Cookie(COOKIE, currentRefreshToken));
		}
		return mockMvc.perform(request);
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
			.header(AuthController.CSRF_HEADER, AuthController.CSRF_HEADER_VALUE)
			.cookie(new Cookie(COOKIE, refreshToken)));
	}

}
