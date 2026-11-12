package com.example.authserver;

import java.security.Principal;
import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Emite os tokens do grant de nome: access token (JWT) e refresh token. Grava a autorização no
 * {@link OAuth2AuthorizationService}, para que depois o grant padrão refresh_token consiga renovar.
 */
public class NameGrantAuthenticationProvider implements AuthenticationProvider {

	private final OAuth2AuthorizationService authorizationService;
	private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

	public NameGrantAuthenticationProvider(OAuth2AuthorizationService authorizationService,
			OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
		this.authorizationService = authorizationService;
		this.tokenGenerator = tokenGenerator;
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		NameGrantAuthenticationToken request = (NameGrantAuthenticationToken) authentication;

		// 1. Quem pediu: precisa ser um cliente autenticado e autorizado a usar este grant.
		if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client) || !client.isAuthenticated()) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
		}
		RegisteredClient registeredClient = client.getRegisteredClient();
		if (registeredClient == null || !registeredClient.getAuthorizationGrantTypes().contains(NameGrant.GRANT_TYPE)) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
		}

		// 2. O usuário. Aqui o "login" é só o nome; num sistema real, entraria a verificação de credenciais.
		Authentication user = UsernamePasswordAuthenticationToken.authenticated(request.getName(), null, List.of(
				new SimpleGrantedAuthority("ROLE_USER"),
				FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));

		DefaultOAuth2TokenContext.Builder tokenContext = DefaultOAuth2TokenContext.builder()
			.registeredClient(registeredClient)
			.principal(user)
			.authorizationServerContext(AuthorizationServerContextHolder.getContext())
			.authorizedScopes(registeredClient.getScopes())
			.authorizationGrantType(NameGrant.GRANT_TYPE)
			.authorizationGrant(request);

		OAuth2Authorization.Builder authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
			.principalName(user.getName())
			.authorizationGrantType(NameGrant.GRANT_TYPE)
			.authorizedScopes(registeredClient.getScopes())
			// O grant refresh_token usa este atributo para saber de quem é a autorização na renovação.
			.attribute(Principal.class.getName(), user);

		// 3. Access token (JWT assinado com a chave RSA do auth-server).
		OAuth2Token generatedAccessToken = tokenGenerator
			.generate(tokenContext.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
		if (generatedAccessToken == null) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Falha ao gerar o access token", null));
		}
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
				generatedAccessToken.getExpiresAt(), registeredClient.getScopes());
		if (generatedAccessToken instanceof ClaimAccessor claims) {
			authorization.token(accessToken,
					metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims()));
		}
		else {
			authorization.accessToken(accessToken);
		}

		// 4. Refresh token, se o cliente puder usar o grant refresh_token.
		OAuth2RefreshToken refreshToken = null;
		if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
			OAuth2Token generatedRefreshToken = tokenGenerator
				.generate(tokenContext.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());
			if (generatedRefreshToken instanceof OAuth2RefreshToken token) {
				refreshToken = token;
				authorization.refreshToken(refreshToken);
			}
		}

		authorizationService.save(authorization.build());
		return new OAuth2AccessTokenAuthenticationToken(registeredClient, client, accessToken, refreshToken);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return NameGrantAuthenticationToken.class.isAssignableFrom(authentication);
	}

}
