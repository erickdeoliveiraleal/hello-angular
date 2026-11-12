package com.example.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UppercaseController.class)
class UppercaseControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devolveONomeEmMaiusculas() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"João\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void nomeAusenteDevolveVazio() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value(""));
	}

}
