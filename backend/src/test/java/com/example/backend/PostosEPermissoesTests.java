package com.example.backend;

import static org.hamcrest.Matchers.contains;
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

/** Posto de trabalho e permissões, sempre a partir de um login de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
class PostosEPermissoesTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void loginComecaNoPrimeiroPostoComAsPermissoesDele() throws Exception {
		MockHttpSession sessao = login();

		mockMvc.perform(get("/api/me").session(sessao))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.postoTrabalho").value("PORTARIA"))
			.andExpect(jsonPath("$.postosDisponiveis", contains("PORTARIA", "CHEFIA")))
			.andExpect(jsonPath("$.permissoes", contains("VISITA_REGISTRAR")));
	}

	@Test
	void naPortariaRegistraVisitaMasNaoEditaEscala() throws Exception {
		MockHttpSession sessao = login();

		acao("/api/visitas", sessao).andExpect(status().isOk());
		// O botão estaria escondido na tela, mas a URL foi chamada direto: o backend recusa.
		acao("/api/escala", sessao).andExpect(status().isForbidden());
	}

	@Test
	void trocarParaChefiaLiberaAEscalaNaHora() throws Exception {
		MockHttpSession sessao = login();

		trocarPosto("CHEFIA", sessao)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.postoTrabalho").value("CHEFIA"))
			.andExpect(jsonPath("$.permissoes", contains("ESCALA_EDITAR", "VISITA_REGISTRAR")));

		acao("/api/escala", sessao)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mensagem").value("Escala editada por erick no posto CHEFIA."));
	}

	@Test
	void voltarParaPortariaTiraAPermissaoNaHora() throws Exception {
		MockHttpSession sessao = login();
		trocarPosto("CHEFIA", sessao).andExpect(status().isOk());

		trocarPosto("PORTARIA", sessao).andExpect(status().isOk());

		acao("/api/escala", sessao).andExpect(status().isForbidden());
	}

	@Test
	void naoTrocaParaPostoQueOUsuarioNaoTem() throws Exception {
		MockHttpSession sessao = login();

		trocarPosto("DIRETORIA", sessao).andExpect(status().isForbidden());

		mockMvc.perform(get("/api/me").session(sessao))
			.andExpect(jsonPath("$.postoTrabalho").value("PORTARIA"));
	}

	@Test
	void semLoginAsAcoesDao401() throws Exception {
		mockMvc.perform(post("/api/visitas").with(csrf())).andExpect(status().isUnauthorized());
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/api/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"erick\",\"password\":\"Erick\"}"))
			.andExpect(status().isOk())
			.andReturn().getRequest().getSession(false);
	}

	private ResultActions trocarPosto(String posto, MockHttpSession sessao) throws Exception {
		return mockMvc.perform(post("/api/posto").with(csrf()).session(sessao)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"posto\":\"" + posto + "\"}"));
	}

	private ResultActions acao(String caminho, MockHttpSession sessao) throws Exception {
		return mockMvc.perform(post(caminho).with(csrf()).session(sessao));
	}

}
