package com.example.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class NameGrantTests {

	private static final String GRANT = "urn:hello:params:oauth:grant-type:name";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void nomeViraAccessTokenERefreshToken() throws Exception {
		ResultActions tokens = nameGrant("joão", "backend-secret")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.access_token").isString())
			.andExpect(jsonPath("$.refresh_token").isString())
			.andExpect(jsonPath("$.token_type").value("Bearer"))
			.andExpect(jsonPath("$.expires_in").isNumber());

		assertThat(payload(field(tokens, "access_token")))
			.contains("\"sub\":\"joão\"")
			.contains("\"iss\":\"http://localhost:9000\"")
			.contains("\"aud\":\"backend\"");
	}

	@Test
	void nomeVazioEhRecusado() throws Exception {
		nameGrant("   ", "backend-secret")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void segredoErradoEhRecusado() throws Exception {
		nameGrant("joão", "segredo-errado").andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRenovaParaOMesmoUsuarioEGiraORefreshToken() throws Exception {
		String firstRefresh = field(nameGrant("joão", "backend-secret"), "refresh_token");

		ResultActions refreshed = refresh(firstRefresh).andExpect(status().isOk());
		assertThat(payload(field(refreshed, "access_token"))).contains("\"sub\":\"joão\"");
		assertThat(field(refreshed, "refresh_token")).isNotEqualTo(firstRefresh);

		// O refresh token antigo deixou de valer.
		refresh(firstRefresh).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void revogacaoInvalidaORefreshToken() throws Exception {
		String refreshToken = field(nameGrant("joão", "backend-secret"), "refresh_token");

		mockMvc.perform(post("/oauth2/revoke")
				.with(httpBasic("backend", "backend-secret"))
				.param("token", refreshToken)
				.param("token_type_hint", "refresh_token"))
			.andExpect(status().isOk());

		refresh(refreshToken).andExpect(status().isBadRequest());
	}

	private ResultActions nameGrant(String name, String secret) throws Exception {
		return mockMvc.perform(post("/oauth2/token")
			.with(httpBasic("backend", secret))
			.param("grant_type", GRANT)
			.param("name", name));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/oauth2/token")
			.with(httpBasic("backend", "backend-secret"))
			.param("grant_type", "refresh_token")
			.param("refresh_token", refreshToken));
	}

	private static String field(ResultActions result, String name) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$." + name);
	}

	private static String payload(String jwt) {
		return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
	}

}
