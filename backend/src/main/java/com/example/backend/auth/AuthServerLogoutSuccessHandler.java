package com.example.backend.auth;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Depois que a sessão do BFF é encerrada, falta encerrar também a sessão no auth-server; senão o
 * próximo "Entrar" logaria direto, sem perguntar o nome. O navegador precisa ir até o auth-server
 * (OpenID Connect RP-Initiated Logout), então devolvemos o endereço em JSON e o Angular navega para lá.
 * (Um redirecionamento HTTP não funcionaria: a chamada do Angular é um fetch, não uma navegação.)
 */
public class AuthServerLogoutSuccessHandler implements LogoutSuccessHandler {

	private final String authServerLogoutUri;

	public AuthServerLogoutSuccessHandler(String authServerLogoutUri) {
		this.authServerLogoutUri = authServerLogoutUri;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		// Página inicial do app, do jeito que o navegador a acessa (ex.: http://127.0.0.1:4200/).
		String appHome = ServletUriComponentsBuilder.fromContextPath(request).path("/").build().toUriString();

		String redirectUrl = appHome;
		if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
			redirectUrl = UriComponentsBuilder.fromUriString(authServerLogoutUri)
				.queryParam("id_token_hint", user.getIdToken().getTokenValue())
				.queryParam("post_logout_redirect_uri", appHome)
				.encode()
				.build()
				.toUriString();
		}

		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"redirectUrl\":\"" + redirectUrl.replace("\"", "%22") + "\"}");
	}

}
