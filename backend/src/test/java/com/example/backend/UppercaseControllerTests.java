package com.example.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UppercaseController.class)
@Import(SessaoObrigatoriaInterceptor.class)
class UppercaseControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devolveONomeEmMaiusculas() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase")
				.session(logado())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"João\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void nomeAusenteDevolveVazio() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase")
				.session(logado())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value(""));
	}

	@Test
	void semSessaoDevolve401() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"João\"}"))
			.andExpect(status().isUnauthorized());
	}

	private static MockHttpSession logado() {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute(SessaoController.ATRIBUTO_NOME, "Ana");
		return sessao;
	}

}
