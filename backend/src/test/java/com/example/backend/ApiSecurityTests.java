package com.example.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class ApiSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void semTokenDevolve401ComOCabecalhoBearer() throws Exception {
		mockMvc.perform(post("/api/uppercase").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"joão\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
	}

	@Test
	void comAccessTokenDevolveMaiusculasEODonoDoToken() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.with(jwt().jwt(token -> token.subject("joão")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("MARIA"))
			.andExpect(jsonPath("$.user").value("joão"));
	}

	@Test
	void tokenInvalidoDevolve401() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.header("Authorization", "Bearer isto-nao-e-um-jwt")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"maria\"}"))
			.andExpect(status().isUnauthorized());
	}

}
