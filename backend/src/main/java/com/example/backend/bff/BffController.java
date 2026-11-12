package com.example.backend.bff;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.UppercaseController.NameRequest;
import com.example.backend.UppercaseController.NameResponse;

/**
 * O que o Angular chama. Exige a sessão do BFF (cookie JSESSIONID); os tokens nunca saem do servidor.
 */
@RestController
@RequestMapping("/bff")
public class BffController {

	/**
	 * @param accessTokenExpiresAt validade do access token guardado na sessão (o token em si não é
	 *                             enviado ao navegador; serve só para ver o BFF renová-lo)
	 */
	public record MeResponse(String user, Instant accessTokenExpiresAt) {
	}

	private final ApiClient api;

	public BffController(ApiClient api) {
		this.api = api;
	}

	/** Usado ao abrir a página (F5): quem está logado, ou 401. */
	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal OidcUser user,
			@RegisteredOAuth2AuthorizedClient("hello") OAuth2AuthorizedClient client) {
		return new MeResponse(user.getSubject(), client.getAccessToken().getExpiresAt());
	}

	/** Repassa a chamada para a API, com o access token do usuário (renovado se preciso). */
	@PostMapping("/uppercase")
	public NameResponse uppercase(@RequestBody NameRequest request) {
		return api.uppercase(request);
	}

}
