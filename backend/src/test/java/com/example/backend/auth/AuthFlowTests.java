package com.example.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

	private static final String COOKIE = AuthController.REFRESH_COOKIE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokenService;

	@Test
	void loginDevolveAccessTokenNoJsonERefreshTokenEmCookieProtegido() throws Exception {
		login("joão", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.expiresIn").value(60))
			.andExpect(jsonPath("$.user").value("joão"))
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(cookie().exists(COOKIE))
			.andExpect(cookie().httpOnly(COOKIE, true))
			.andExpect(cookie().secure(COOKIE, true))
			.andExpect(cookie().sameSite(COOKIE, "Strict"))
			.andExpect(cookie().path(COOKIE, "/api/auth"))
			.andExpect(cookie().maxAge(COOKIE, 86400));
	}

	@Test
	void loginComNomeVazioDevolve400() throws Exception {
		login("   ", null).andExpect(status().isBadRequest());
	}

	@Test
	void endpointsDeAuthSemHeaderAntiCsrfDevolvem403() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"joão\"}"))
			.andExpect(status().isForbidden());
		String refresh = refreshCookie(login("joão", null));
		mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, refresh)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, refresh)))
			.andExpect(status().isForbidden());
	}

	@Test
	void uppercaseSemTokenDevolve401() throws Exception {
		uppercase(null, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void uppercaseComAccessTokenDevolveMaiusculas() throws Exception {
		String access = accessToken(login("joão", null));
		uppercase(access, "joão")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void refreshTokenNaoServeComoAccessToken() throws Exception {
		String refresh = refreshCookie(login("joão", null));
		uppercase(refresh, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenExpiradoDevolve401() throws Exception {
		Instant past = Instant.now().minusSeconds(120);
		String expired = tokenService.encode("joão", SecurityConfig.ACCESS, past.minusSeconds(60), past);
		uppercase(expired, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void refreshPeloCookieGeraNovosTokensEUsoRepetidoEhRecusado() throws Exception {
		String refresh = refreshCookie(login("joão", null));

		ResultActions refreshed = refresh(refresh)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user").value("joão"))
			.andExpect(cookie().exists(COOKIE));
		uppercase(accessToken(refreshed), "maria")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("MARIA"));

		refresh(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshSemCookieDevolve401() throws Exception {
		mockMvc.perform(post("/api/auth/refresh").header(AuthController.CSRF_HEADER, AuthController.CSRF_HEADER_VALUE))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenNaoServeComoRefreshToken() throws Exception {
		String access = accessToken(login("joão", null));
		refresh(access).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutApagaOCookieERevogaORefreshToken() throws Exception {
		String refresh = refreshCookie(login("joão", null));

		mockMvc.perform(post("/api/auth/logout")
				.header(AuthController.CSRF_HEADER, AuthController.CSRF_HEADER_VALUE)
				.cookie(new Cookie(COOKIE, refresh)))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge(COOKIE, 0))
			.andExpect(cookie().value(COOKIE, ""));

		refresh(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void novoLoginRevogaASessaoAnterior() throws Exception {
		String first = refreshCookie(login("joão", null));
		login("maria", first).andExpect(status().isOk());

		refresh(first).andExpect(status().isUnauthorized());
	}

	private ResultActions login(String name, String currentRefreshToken) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/auth/login")
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

	private ResultActions uppercase(String accessToken, String name) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/uppercase")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\"}");
		if (accessToken != null) {
			request.header("Authorization", "Bearer " + accessToken);
		}
		return mockMvc.perform(request);
	}

	private static String accessToken(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private static String refreshCookie(ResultActions result) {
		return result.andReturn().getResponse().getCookie(COOKIE).getValue();
	}

}
