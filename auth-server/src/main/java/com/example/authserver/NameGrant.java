package com.example.authserver;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * O grant type customizado deste projeto: "troque um nome por tokens".
 *
 * A especificação do OAuth 2 permite grant types de extensão, identificados por uma URI absoluta
 * (RFC 6749, seção 4.5). Exemplos reais: o token exchange (urn:ietf:params:oauth:grant-type:token-exchange)
 * e o JWT bearer (urn:ietf:params:oauth:grant-type:jwt-bearer).
 *
 * ATENÇÃO: este grant é, na prática, o antigo "password grant" sem senha, que o OAuth 2.1 removeu.
 * O auth-server confia no cliente (o backend) para dizer quem é o usuário. Existe aqui só para manter
 * o campo de nome do Angular fazendo o login, como você pediu.
 */
public final class NameGrant {

	public static final AuthorizationGrantType GRANT_TYPE = new AuthorizationGrantType(
			"urn:hello:params:oauth:grant-type:name");

	/** Parâmetro do POST /oauth2/token com o nome digitado. */
	public static final String NAME_PARAMETER = "name";

	private NameGrant() {
	}

}
