package com.example.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UppercaseControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devolveONomeEmMaiusculas() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase").with(user("Ana")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"João\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void nomeAusenteDevolveVazio() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase").with(user("Ana")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value(""));
	}

	@Test
	void semSessaoDevolve401() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"João\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logadoMasSemTokenCsrfDevolve403() throws Exception {
		mockMvc.perform(post("/api/protegido/uppercase").with(user("Ana"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"João\"}"))
			.andExpect(status().isForbidden());
	}

}
