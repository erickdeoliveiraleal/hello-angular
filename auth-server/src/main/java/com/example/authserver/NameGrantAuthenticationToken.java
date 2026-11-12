package com.example.authserver;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

/** O pedido de token com o grant de nome: quem pediu (o cliente) e o nome do usuário. */
public class NameGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

	private final String name;

	public NameGrantAuthenticationToken(String name, Authentication clientPrincipal,
			Map<String, Object> additionalParameters) {
		super(NameGrant.GRANT_TYPE, clientPrincipal, additionalParameters);
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
