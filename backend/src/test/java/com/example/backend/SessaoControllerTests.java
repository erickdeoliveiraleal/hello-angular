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

@SpringBootTest
@AutoConfigureMockMvc
class SessaoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void semSessaoMeDevolve401() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginGuardaOUsuarioNaSessao() throws Exception {
		MockHttpSession sessao = login(" João ");

		mockMvc.perform(get("/api/me").session(sessao))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("João"));
	}

	@Test
	void nomeVazioDevolve400() throws Exception {
		mockMvc.perform(post("/api/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  \"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void loginSemTokenCsrfDevolve403() throws Exception {
		mockMvc.perform(post("/api/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void logoutInvalidaASessao() throws Exception {
		MockHttpSession sessao = login("Ana");

		mockMvc.perform(post("/api/logout").with(csrf()).session(sessao)).andExpect(status().isNoContent());

		assertThat(sessao.isInvalid()).isTrue();
	}

	private MockHttpSession login(String nome) throws Exception {
		MockHttpSession sessao = (MockHttpSession) mockMvc.perform(post("/api/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + nome + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(nome.trim()))
			.andReturn().getRequest().getSession(false);
		assertThat(sessao).isNotNull();
		return sessao;
	}

}
