package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
class AuthSessionTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void semSessaoMeDevolve401() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginSemTokenCsrfDevolve403() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"joão\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void loginComNomeVazioDevolve400() throws Exception {
		login("   ", null).andExpect(status().isBadRequest());
	}

	@Test
	void loginGuardaOUsuarioNaSessao() throws Exception {
		MockHttpSession session = sessionOf(login("joão", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.user").value("joão")));

		mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user").value("joão"));
	}

	@Test
	void uppercaseSemSessaoDevolve401() throws Exception {
		uppercase(null, "joão").andExpect(status().isUnauthorized());
	}

	@Test
	void uppercaseComSessaoDevolveMaiusculas() throws Exception {
		MockHttpSession session = sessionOf(login("joão", null));
		uppercase(session, "joão")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("JOÃO"));
	}

	@Test
	void uppercaseComSessaoMasSemCsrfDevolve403() throws Exception {
		MockHttpSession session = sessionOf(login("joão", null));
		mockMvc.perform(post("/api/uppercase").session(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"joão\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void loginTrocaOIdDaSessaoExistente() throws Exception {
		MockHttpSession existing = new MockHttpSession();
		String idBefore = existing.getId();

		MockHttpSession after = sessionOf(login("joão", existing));

		assertThat(after.getId()).isNotEqualTo(idBefore);
	}

	@Test
	void logoutInvalidaASessaoEApagaOCookie() throws Exception {
		MockHttpSession session = sessionOf(login("joão", null));

		mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("JSESSIONID", 0));

		assertThat(session.isInvalid()).isTrue();
	}

	private ResultActions login(String name, MockHttpSession session) throws Exception {
		var request = post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\"}");
		if (session != null) {
			request.session(session);
		}
		return mockMvc.perform(request);
	}

	private ResultActions uppercase(MockHttpSession session, String name) throws Exception {
		var request = post("/api/uppercase").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\"}");
		if (session != null) {
			request.session(session);
		}
		return mockMvc.perform(request);
	}

	private static MockHttpSession sessionOf(ResultActions result) {
		return (MockHttpSession) result.andReturn().getRequest().getSession(false);
	}

}
