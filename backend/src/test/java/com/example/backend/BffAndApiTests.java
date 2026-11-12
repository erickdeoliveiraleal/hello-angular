package com.example.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.List;

import org.hamcrest.Matchers;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.backend.UppercaseController.NameResponse;
import com.example.backend.bff.ApiClient;

@SpringBootTest
@AutoConfigureMockMvc
class BffAndApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	/** A chamada HTTP de verdade do BFF para a API é substituída: aqui testamos cada lado separado. */
	@MockitoBean
	private ApiClient apiClient;

	// ----- API: só aceita access token JWT -----

	@Test
	void apiSemTokenDevolve401() throws Exception {
		mockMvc.perform(post("/api/uppercase").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"joão\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void apiComAccessTokenDevolveMaiusculasEODonoDoToken() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.with(jwt().jwt(token -> token.subject("joão")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("MARIA"))
			.andExpect(jsonPath("$.user").value("joão"));
	}

	@Test
	void apiNaoAceitaASessaoDoNavegador() throws Exception {
		// Uma sessão com usuário logado, como a que o BFF cria. A API não olha sessão: só o Bearer token.
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("joão", null, List.of()));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

		mockMvc.perform(post("/api/uppercase").session(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isUnauthorized());
	}

	// ----- BFF: sessão do navegador -----

	@Test
	void bffSemLoginDevolve401() throws Exception {
		mockMvc.perform(get("/bff/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void entrarRedirecionaParaOAuthServer() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/hello"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", Matchers.startsWith("http://localhost:9000/oauth2/authorize?")))
			// PKCE: o BFF manda o hash de um segredo que só ele conhece e o apresenta na troca do código.
			.andExpect(header().string("Location", Matchers.containsString("code_challenge_method=S256")));
	}

	@Test
	void bffMeDevolveOUsuarioLogado() throws Exception {
		mockMvc.perform(get("/bff/me").with(login()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user").value("joão"))
			// O access token nunca vai para o navegador; só a validade dele.
			.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@Test
	void bffRepassaParaAApi() throws Exception {
		given(apiClient.uppercase(any())).willReturn(new NameResponse("MARIA", "joão"));

		mockMvc.perform(post("/bff/uppercase").with(login()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("MARIA"));
	}

	@Test
	void bffSemCsrfDevolve403() throws Exception {
		mockMvc.perform(post("/bff/uppercase").with(login())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void logoutDevolveOEnderecoDeLogoutDoAuthServer() throws Exception {
		mockMvc.perform(post("/logout").with(login()).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.redirectUrl", Matchers.startsWith("http://localhost:9000/connect/logout?id_token_hint=")))
			.andExpect(jsonPath("$.redirectUrl", Matchers.containsString("post_logout_redirect_uri=http://localhost/")));
	}

	private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor login() {
		return oidcLogin()
			.clientRegistration(clientRegistrations.findByRegistrationId("hello"))
			.idToken(token -> token.subject("joão"));
	}

}
