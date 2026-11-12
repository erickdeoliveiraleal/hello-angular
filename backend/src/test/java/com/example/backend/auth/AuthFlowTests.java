package com.example.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokenService;

	@Test
	void loginDevolveAccessERefreshToken() throws Exception {
		login("joão")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.refreshToken").isString())
			.andExpect(jsonPath("$.expiresIn").value(60));
	}

	@Test
	void loginComNomeVazioDevolve400() throws Exception {
		login("   ").andExpect(status().isBadRequest());
	}

	@Test
	void uppercaseSemTokenDevolve401() throws Exception {
		uppercase(null, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void uppercaseComAccessTokenDevolveMaiusculas() throws Exception {
		String access = field(login("joão"), "accessToken");
		uppercase(access, "joão")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void refreshTokenNaoServeComoAccessToken() throws Exception {
		String refresh = field(login("joão"), "refreshToken");
		uppercase(refresh, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenExpiradoDevolve401() throws Exception {
		Instant past = Instant.now().minusSeconds(120);
		String expired = tokenService.encode("joão", SecurityConfig.ACCESS, past.minusSeconds(60), past);
		uppercase(expired, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void refreshGeraNovosTokensEUsoRepetidoEhRecusado() throws Exception {
		String refresh = field(login("joão"), "refreshToken");

		ResultActions refreshed = refresh(refresh)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshToken").isString());
		uppercase(field(refreshed, "accessToken"), "maria")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("MARIA"));

		refresh(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenNaoServeComoRefreshToken() throws Exception {
		String access = field(login("joão"), "accessToken");
		refresh(access).andExpect(status().isUnauthorized());
	}

	private ResultActions login(String name) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\"}"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
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

	private static String field(ResultActions result, String name) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$." + name);
	}

}
