package com.example.backend.bff;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.backend.UppercaseController.NameRequest;
import com.example.backend.UppercaseController.NameResponse;

/**
 * Cliente HTTP do BFF para a API. O interceptor pega o access token do usuário logado (na sessão),
 * renova se estiver expirando e coloca no header "Authorization: Bearer ...".
 */
@Component
public class ApiClient {

	private final RestClient restClient;

	public ApiClient(OAuth2AuthorizedClientManager authorizedClientManager,
			@Value("${app.api-base-url}") String apiBaseUrl) {
		this.restClient = RestClient.builder()
			.baseUrl(apiBaseUrl)
			.requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager))
			.build();
	}

	public NameResponse uppercase(NameRequest request) {
		return restClient.post()
			.uri("/api/uppercase")
			.attributes(clientRegistrationId("hello"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(NameResponse.class);
	}

}
