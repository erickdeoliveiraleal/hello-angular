package com.example.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationCodeFlowTests {

	private static final String REDIRECT_URI = "http://127.0.0.1:4200/login/oauth2/code/hello";

	/** PKCE: o cliente guarda o verifier e manda só o hash (challenge) no /authorize. */
	private static final String CODE_VERIFIER = "um-verifier-de-teste-com-mais-de-quarenta-e-tres-caracteres-123";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicaAConfiguracaoOpenIdConnect() throws Exception {
		mockMvc.perform(get("/.well-known/openid-configuration"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
			.andExpect(jsonPath("$.end_session_endpoint").value("http://localhost:9000/connect/logout"));
	}

	@Test
	void authorizeSemLoginLevaParaAPaginaDeLogin() throws Exception {
		mockMvc.perform(authorize().accept(MediaType.TEXT_HTML))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void paginaDeLoginTemSoOCampoDeNome() throws Exception {
		mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"username\"")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
	}

	@Test
	void loginComNomeVazioVoltaComErro() throws Exception {
		mockMvc.perform(post("/login").param("username", "  ").with(csrf()))
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void fluxoCompletoAuthorizationCodeComRotacaoDoRefreshToken() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/login").session(session).param("username", "joão").with(csrf()))
			.andExpect(status().is3xxRedirection());

		// Com a sessão do login, o /authorize devolve um código para o redirect_uri do cliente.
		String location = mockMvc.perform(authorize().session(session))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern(REDIRECT_URI + "?code=*&state=xyz"))
			.andReturn().getResponse().getRedirectedUrl();
		String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

		// O BFF troca o código por tokens, autenticando-se com client_id e segredo.
		ResultActions tokens = mockMvc.perform(post("/oauth2/token")
				.with(httpBasic("bff", "bff-secret"))
				.param("grant_type", "authorization_code")
				.param("code", code)
				.param("redirect_uri", REDIRECT_URI)
				.param("code_verifier", CODE_VERIFIER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.access_token").isString())
			.andExpect(jsonPath("$.refresh_token").isString())
			.andExpect(jsonPath("$.id_token").isString());

		String accessToken = field(tokens, "access_token");
		assertThat(payload(accessToken)).contains("\"sub\":\"joão\"").contains("\"iss\":\"http://localhost:9000\"");

		// Refresh: devolve um refresh token novo, e o antigo deixa de valer.
		String firstRefresh = field(tokens, "refresh_token");
		String secondRefresh = field(refresh(firstRefresh).andExpect(status().isOk()), "refresh_token");
		assertThat(secondRefresh).isNotEqualTo(firstRefresh);

		refresh(firstRefresh).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void tokenComSegredoErradoEhRecusado() throws Exception {
		mockMvc.perform(post("/oauth2/token")
				.with(httpBasic("bff", "segredo-errado"))
				.param("grant_type", "authorization_code")
				.param("code", "qualquer")
				.param("redirect_uri", REDIRECT_URI))
			.andExpect(status().isUnauthorized());
	}

	/** Os parâmetros vão na query string: é dali que o servidor de autorização os lê num GET. */
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize() {
		String uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
			.queryParam("response_type", "code")
			.queryParam("client_id", "bff")
			.queryParam("scope", "openid profile")
			.queryParam("redirect_uri", REDIRECT_URI)
			.queryParam("state", "xyz")
			.queryParam("code_challenge", codeChallenge())
			.queryParam("code_challenge_method", "S256")
			.encode()
			.build()
			.toUriString();
		return get(java.net.URI.create(uri));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/oauth2/token")
			.with(httpBasic("bff", "bff-secret"))
			.param("grant_type", "refresh_token")
			.param("refresh_token", refreshToken));
	}

	private static String field(ResultActions result, String name) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$." + name);
	}

	private static String codeChallenge() {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
				.digest(CODE_VERIFIER.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String payload(String jwt) {
		return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
	}

}
