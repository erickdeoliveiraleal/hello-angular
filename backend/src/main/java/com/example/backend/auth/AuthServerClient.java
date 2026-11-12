package com.example.backend.auth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conversa com o auth-server (servidor a servidor), autenticando-se como o cliente "backend".
 * Usa o grant customizado de nome para o login e os endpoints padrão para refresh e revogação.
 */
@Component
public class AuthServerClient {

	static final String NAME_GRANT = "urn:hello:params:oauth:grant-type:name";

	/** O que o auth-server devolve no /oauth2/token (só o que interessa aqui). */
	public record Tokens(String accessToken, String refreshToken, long expiresIn) {
	}

	private final RestClient restClient;

	public AuthServerClient(@Value("${app.auth-server.url}") String authServerUrl,
			@Value("${app.auth-server.client-id}") String clientId,
			@Value("${app.auth-server.client-secret}") String clientSecret) {
		this.restClient = RestClient.builder()
			.baseUrl(authServerUrl)
			.defaultHeaders(headers -> headers.setBasicAuth(clientId, clientSecret))
			.build();
	}

	/** Login: troca o nome digitado por tokens (grant customizado). */
	public Tokens tokensForName(String name) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", NAME_GRANT);
		form.add("name", name);
		return requestTokens(form, HttpStatus.BAD_REQUEST);
	}

	/** Refresh: troca o refresh token por um par novo (o antigo deixa de valer). */
	public Tokens refresh(String refreshToken) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "refresh_token");
		form.add("refresh_token", refreshToken);
		return requestTokens(form, HttpStatus.UNAUTHORIZED);
	}

	/** Logout: revoga o refresh token no auth-server (RFC 7009). */
	public void revoke(String refreshToken) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("token", refreshToken);
		form.add("token_type_hint", "refresh_token");
		try {
			restClient.post().uri("/oauth2/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
				.retrieve().toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			// Token já inválido: para o logout, tanto faz.
		}
	}

	private Tokens requestTokens(MultiValueMap<String, String> form, HttpStatus statusWhenRejected) {
		try {
			Map<String, Object> body = restClient.post()
				.uri("/oauth2/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {
				});
			return new Tokens((String) body.get("access_token"), (String) body.get("refresh_token"),
					((Number) body.get("expires_in")).longValue());
		}
		catch (RestClientResponseException ex) {
			throw new ResponseStatusException(statusWhenRejected, "O auth-server recusou o pedido de tokens");
		}
	}

}
