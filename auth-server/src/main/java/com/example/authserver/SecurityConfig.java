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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class SecurityConfig {

	/** Endpoints do OAuth 2 / OpenID Connect: /oauth2/authorize, /oauth2/token, /oauth2/jwks, /userinfo... */
	@Bean
	@Order(1)
	SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.oauth2AuthorizationServer(authorizationServer -> {
				http.securityMatcher(authorizationServer.getEndpointsMatcher());
				authorizationServer.oidc(Customizer.withDefaults());
			})
			.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
			// Usa o corsConfigurationSource abaixo: o Angular chama o /oauth2/token direto do navegador.
			.cors(Customizer.withDefaults())
			// Navegador sem login no /oauth2/authorize: vai para a página de login.
			.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
					new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
		return http.build();
	}

	/** O resto: a página de login (só nome) e o formulário que ela envia. */
	@Bean
	@Order(2)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/login", "/error").permitAll()
				.anyRequest().authenticated())
			.formLogin(form -> form.loginPage("/login"));
		return http.build();
	}

	@Bean
	AuthenticationProvider nameOnlyAuthenticationProvider() {
		return new NameOnlyAuthenticationProvider();
	}

	/** Os clientes que podem pedir tokens. Aqui, só o próprio Angular. */
	@Bean
	RegisteredClientRepository registeredClientRepository(@Value("${app.angular.client-id}") String clientId,
			@Value("${app.angular.redirect-uri}") String redirectUri,
			@Value("${app.angular.post-logout-redirect-uri}") String postLogoutRedirectUri,
			@Value("${app.tokens.access-token-ttl}") Duration accessTokenTtl) {
		RegisteredClient angular = RegisteredClient.withId(UUID.randomUUID().toString())
			.clientId(clientId)
			// Cliente PÚBLICO: o código roda no navegador, então não há como guardar um segredo.
			// No lugar do segredo entra o PKCE, que prova que quem troca o código é quem pediu o login.
			.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(redirectUri)
			.postLogoutRedirectUri(postLogoutRedirectUri)
			.scope(OidcScopes.OPENID)
			.scope(OidcScopes.PROFILE)
			.clientSettings(ClientSettings.builder()
				.requireAuthorizationConsent(false)
				.requireProofKey(true)
				.build())
			// Sem refresh token: o Spring Authorization Server não emite refresh token para clientes
			// públicos. Quando o access token expira, o app pede um novo ao auth-server; como a sessão
			// de lá continua ativa, isso acontece sem pedir o nome de novo.
			.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(accessTokenTtl).build())
			.build();
		return new InMemoryRegisteredClientRepository(angular);
	}

	/**
	 * O Angular (em 127.0.0.1:4200) chama o /oauth2/token direto do navegador, numa origem diferente
	 * da do auth-server. Sem CORS, o navegador bloquearia a resposta.
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.angular.origin}") String angularOrigin) {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin(angularOrigin);
		config.addAllowedMethod("GET");
		config.addAllowedMethod("POST");
		config.addAllowedHeader("*");
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/**
	 * Par de chaves RSA que assina os tokens (RS256). A chave pública fica em /oauth2/jwks, de onde o
	 * backend a busca para validar os tokens. Gerada a cada inicialização: reiniciar o servidor de
	 * autorização invalida os tokens emitidos antes. Em produção, a chave é persistida e rotacionada.
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

	/** Usado pelo endpoint /userinfo do OpenID Connect para validar o access token. */
	@Bean
	JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
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
