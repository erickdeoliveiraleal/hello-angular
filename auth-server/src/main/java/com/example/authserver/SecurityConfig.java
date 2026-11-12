package com.example.authserver;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class SecurityConfig {

	/**
	 * Endpoints do OAuth 2 (/oauth2/token, /oauth2/revoke, /oauth2/jwks...), com o grant de nome
	 * adicionado ao /oauth2/token. Não há tela de login: quem chama é o backend, servidor a servidor.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
			OAuth2AuthorizationService authorizationService,
			OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) throws Exception {
		http
			.oauth2AuthorizationServer(authorizationServer -> {
				http.securityMatcher(authorizationServer.getEndpointsMatcher());
				authorizationServer.tokenEndpoint(tokenEndpoint -> tokenEndpoint
					.accessTokenRequestConverter(new NameGrantAuthenticationConverter())
					.authenticationProvider(new NameGrantAuthenticationProvider(authorizationService, tokenGenerator)));
			})
			.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
		return http.build();
	}

	/** Qualquer outro endereço: negado. */
	@Bean
	@Order(2)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers("/error").permitAll()
			.anyRequest().denyAll());
		return http.build();
	}

	/** O cliente registrado: o backend, que troca o nome digitado por tokens. */
	@Bean
	RegisteredClientRepository registeredClientRepository(@Value("${app.backend.client-id}") String clientId,
			@Value("${app.backend.client-secret}") String clientSecret,
			@Value("${app.tokens.access-token-ttl}") Duration accessTokenTtl,
			@Value("${app.tokens.refresh-token-ttl}") Duration refreshTokenTtl) {
		RegisteredClient backend = RegisteredClient.withId(UUID.randomUUID().toString())
			.clientId(clientId)
			// {noop}: segredo sem hash, aceitável só em desenvolvimento.
			.clientSecret("{noop}" + clientSecret)
			// Cliente confidencial: o backend roda no servidor e guarda o segredo.
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(NameGrant.GRANT_TYPE)
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			.scope("hello")
			.tokenSettings(TokenSettings.builder()
				.accessTokenTimeToLive(accessTokenTtl)
				.refreshTokenTimeToLive(refreshTokenTtl)
				// Rotação: cada refresh devolve um refresh token novo, e o antigo deixa de valer.
				.reuseRefreshTokens(false)
				.build())
			.build();
		return new InMemoryRegisteredClientRepository(backend);
	}

	/** Onde ficam as autorizações emitidas (tokens válidos). Em memória: reiniciar invalida tudo. */
	@Bean
	OAuth2AuthorizationService authorizationService() {
		return new InMemoryOAuth2AuthorizationService();
	}

	/** Gera os tokens: access token JWT (RS256) e refresh token opaco. */
	@Bean
	OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
		return new DelegatingOAuth2TokenGenerator(new JwtGenerator(new NimbusJwtEncoder(jwkSource)),
				new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
	}

	/**
	 * Par de chaves RSA que assina os tokens. A chave pública fica em /oauth2/jwks, de onde o backend
	 * a busca para validar os tokens. Gerada a cada inicialização (em produção, persistida e rotacionada).
	 */
	@Bean
	JWKSource<SecurityContext> jwkSource() {
		KeyPair keyPair = generateRsaKey();
		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
			.privateKey((RSAPrivateKey) keyPair.getPrivate())
			.keyID(UUID.randomUUID().toString())
			.build();
		return new ImmutableJWKSet<>(new JWKSet(rsaKey));
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(@Value("${app.issuer}") String issuer) {
		return AuthorizationServerSettings.builder().issuer(issuer).build();
	}

	private static KeyPair generateRsaKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
