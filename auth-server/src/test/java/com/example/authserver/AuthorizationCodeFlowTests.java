package com.example.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationCodeFlowTests {

	private static final String REDIRECT_URI = "http://127.0.0.1:4200/";

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
		mockMvc.perform(authorize(true).accept(MediaType.TEXT_HTML))
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
	void fluxoCompletoComClientePublicoEPkce() throws Exception {
		String code = loginAndGetCode();

		// O Angular troca o código por tokens SEM segredo: no lugar dele, apresenta o code_verifier (PKCE).
		ResultActions tokens = exchange(code, CODE_VERIFIER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.access_token").isString())
			.andExpect(jsonPath("$.id_token").isString())
			// Cliente público não recebe refresh token.
			.andExpect(jsonPath("$.refresh_token").doesNotExist());

		String accessToken = field(tokens, "access_token");
		assertThat(payload(accessToken)).contains("\"sub\":\"joão\"").contains("\"iss\":\"http://localhost:9000\"");
	}

	@Test
	void codeVerifierErradoEhRecusado() throws Exception {
		String code = loginAndGetCode();

		exchange(code, "outro-verifier-que-nao-gerou-o-challenge-enviado-no-authorize")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void authorizeSemPkceEhRecusado() throws Exception {
		mockMvc.perform(authorize(false).session(login()))
			.andExpect(redirectedUrlPattern(REDIRECT_URI + "?error=invalid_request*"));
	}

	@Test
	void corsLiberaOAngularNoTokenEndpoint() throws Exception {
		mockMvc.perform(options("/oauth2/token")
				.header("Origin", "http://127.0.0.1:4200")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4200"));
	}

	@Test
	void corsNaoLiberaOutrosSites() throws Exception {
		mockMvc.perform(options("/oauth2/token")
				.header("Origin", "http://site-malicioso.example")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden());
	}

	private MockHttpSession login() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/login").session(session).param("username", "joão").with(csrf()))
			.andExpect(status().is3xxRedirection());
		return session;
	}

	private String loginAndGetCode() throws Exception {
		String location = mockMvc.perform(authorize(true).session(login()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern(REDIRECT_URI + "?code=*&state=xyz"))
			.andReturn().getResponse().getRedirectedUrl();
		return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
	}

	private ResultActions exchange(String code, String codeVerifier) throws Exception {
		return mockMvc.perform(post("/oauth2/token")
			.param("grant_type", "authorization_code")
			.param("client_id", "angular")
			.param("code", code)
			.param("redirect_uri", REDIRECT_URI)
			.param("code_verifier", codeVerifier));
	}

	/** Os parâmetros vão na query string: é dali que o servidor de autorização os lê num GET. */
	private MockHttpServletRequestBuilder authorize(boolean withPkce) {
		UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
			.queryParam("response_type", "code")
			.queryParam("client_id", "angular")
			.queryParam("scope", "openid profile")
			.queryParam("redirect_uri", REDIRECT_URI)
			.queryParam("state", "xyz");
		if (withPkce) {
			uri.queryParam("code_challenge", codeChallenge()).queryParam("code_challenge_method", "S256");
		}
		return get(java.net.URI.create(uri.encode().build().toUriString()));
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
