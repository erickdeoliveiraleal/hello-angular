package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessaoController.class)
class SessaoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void semSessaoMeDevolve401() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginGuardaONomeNaSessao() throws Exception {
		MockHttpSession sessao = (MockHttpSession) mockMvc.perform(post("/api/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" João \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("João"))
			.andReturn().getRequest().getSession(false);

		assertThat(sessao).isNotNull();
		mockMvc.perform(get("/api/me").session(sessao))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("João"));
	}

	@Test
	void nomeVazioDevolve400() throws Exception {
		mockMvc.perform(post("/api/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  \"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void logoutInvalidaASessao() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute(SessaoController.ATRIBUTO_NOME, "Ana");

		mockMvc.perform(post("/api/logout").session(sessao)).andExpect(status().isNoContent());

		assertThat(sessao.isInvalid()).isTrue();
	}

}
