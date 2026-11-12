package com.example.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MensagemControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void mensagemPublicaNaoPrecisaDeSessao() throws Exception {
		mockMvc.perform(get("/api/publico/mensagem"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mensagem").isNotEmpty());
	}

	@Test
	void horaLoginSemSessaoDevolve401() throws Exception {
		mockMvc.perform(get("/api/protegido/hora-login")).andExpect(status().isUnauthorized());
	}

	@Test
	void horaLoginComSessaoDevolveNomeEHora() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute(SessaoController.ATRIBUTO_HORA_LOGIN, Instant.parse("2026-11-12T20:00:00Z"));

		mockMvc.perform(get("/api/protegido/hora-login").with(user("Ana")).session(sessao))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Ana"))
			.andExpect(jsonPath("$.horaLogin").value("2026-11-12T20:00:00Z"));
	}

}
