package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/** Chain do app móvel: Bearer JWT, sem sessão e sem CSRF. */
@SpringBootTest
@AutoConfigureMockMvc
class MobileBearerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MobileAuthController mobileAuthController;

	@Test
	void tokenDevolveJwtNoJsonSemCsrfESemSessao() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/mobile/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"joão\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.user").value("joão"))
			.andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
	}

	@Test
	void tokenComNomeVazioDevolve400() throws Exception {
		mockMvc.perform(post("/api/mobile/token").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void uppercaseComBearerFuncionaSemCsrf() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.header("Authorization", "Bearer " + tokenFor("joão"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"joão\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void meComBearerDevolveOUsuarioDoToken() throws Exception {
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenFor("maria")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user").value("maria"));
	}

	@Test
	void bearerInvalidoDevolve401() throws Exception {
		mockMvc.perform(post("/api/uppercase")
				.header("Authorization", "Bearer inventado")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"joão\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void bearerExpiradoDevolve401() throws Exception {
		Instant past = Instant.now().minusSeconds(3600);
		String expired = mobileAuthController.encode("joão", past, past.plusSeconds(60));

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
			.andExpect(status().isUnauthorized());
	}

	/** Quem manda Bearer cai na chain do app, que ignora a sessão: um JSESSIONID válido junto não ajuda. */
	@Test
	void bearerInvalidoComSessaoValidaContinua401() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"joão\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getRequest()
			.getSession(false);

		mockMvc.perform(get("/api/auth/me").session(session).header("Authorization", "Bearer inventado"))
			.andExpect(status().isUnauthorized());
	}

	private String tokenFor(String name) throws Exception {
		String body = mockMvc.perform(post("/api/mobile/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
