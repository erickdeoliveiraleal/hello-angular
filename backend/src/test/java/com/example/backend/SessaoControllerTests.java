package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SessaoControllerTests {

	/** Senha de estudo do usuário erick (o código de produção só tem o hash). */
	private static final String SENHA = "Erick";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void semSessaoMeDevolve401() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginGuardaOUsuarioNaSessao() throws Exception {
		MockHttpSession sessao = login(" Erick ", SENHA);

		// O nome vem como foi cadastrado ("erick"), não como foi digitado.
		mockMvc.perform(get("/api/me").session(sessao))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("erick"));
	}

	@Test
	void nomeVazioDevolve400() throws Exception {
		tentarLogin("  ", SENHA).andExpect(status().isBadRequest());
	}

	@Test
	void senhaVaziaDevolve400() throws Exception {
		tentarLogin("erick", "").andExpect(status().isBadRequest());
	}

	@Test
	void senhaErradaDevolve401ESemSessao() throws Exception {
		MockHttpSession sessao = (MockHttpSession) tentarLogin("erick", "errada")
			.andExpect(status().isUnauthorized())
			.andReturn().getRequest().getSession(false);

		assertThat(sessao).isNull();
	}

	@Test
	void senhaDiferenciaMaiusculas() throws Exception {
		tentarLogin("erick", SENHA.toLowerCase()).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioInexistenteDevolveOMesmo401() throws Exception {
		tentarLogin("ana", SENHA).andExpect(status().isUnauthorized());
	}

	@Test
	void loginSemTokenCsrfDevolve403() throws Exception {
		mockMvc.perform(post("/api/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpo("erick", SENHA)))
			.andExpect(status().isForbidden());
	}

	@Test
	void logoutInvalidaASessao() throws Exception {
		MockHttpSession sessao = login("erick", SENHA);

		mockMvc.perform(post("/api/logout").with(csrf()).session(sessao)).andExpect(status().isNoContent());

		assertThat(sessao.isInvalid()).isTrue();
	}

	private MockHttpSession login(String nome, String senha) throws Exception {
		MockHttpSession sessao = (MockHttpSession) tentarLogin(nome, senha)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("erick"))
			.andReturn().getRequest().getSession(false);
		assertThat(sessao).isNotNull();
		return sessao;
	}

	private ResultActions tentarLogin(String nome, String senha) throws Exception {
		return mockMvc.perform(post("/api/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(corpo(nome, senha)));
	}

	private static String corpo(String nome, String senha) {
		return "{\"name\":\"" + nome + "\",\"password\":\"" + senha + "\"}";
	}

}
