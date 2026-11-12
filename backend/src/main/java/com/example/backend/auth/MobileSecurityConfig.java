package com.example.backend.auth;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Segunda porta de entrada, pensada para um app nativo (Android, iOS): em vez do cookie de sessão,
 * o cliente manda {@code Authorization: Bearer <jwt>} em cada requisição.
 *
 * <p>O Spring percorre as chains em ordem e usa a primeira cujo {@code securityMatcher} aceitar a requisição.
 * Esta é a {@code @Order(1)}; tudo o que ela não pegar cai na chain de sessão ({@link SecurityConfig}).
 * Ela não descobre se o cliente "é um celular": só olha como a requisição chega.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class MobileSecurityConfig {

	static final String MOBILE_PATHS = "/api/mobile/**";
	static final String TOKEN_ENDPOINT = "/api/mobile/token";

	/** Requisições com Bearer token, ou para /api/mobile/** (onde o app pega o token, antes de ter um). */
	static final RequestMatcher BEARER_OR_MOBILE = new RequestMatcher() {

		private final RequestMatcher mobilePaths = PathPatternRequestMatcher.withDefaults().matcher(MOBILE_PATHS);

		@Override
		public boolean matches(HttpServletRequest request) {
			String authorization = request.getHeader("Authorization");
			boolean bearer = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
			return bearer || mobilePaths.matches(request);
		}

	};

	@Bean
	@Order(1)
	SecurityFilterChain mobileSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher(BEARER_OR_MOBILE)
			// Sem sessão: não cria nem lê JSESSIONID. Um cookie de sessão enviado junto é ignorado,
			// então só o token decide quem é o usuário.
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Sem cookie, não há CSRF: o navegador não anexa o token sozinho, quem o envia é o código do app.
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, TOKEN_ENDPOINT).permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			// Valida o JWT (assinatura, validade, emissor). Token ausente ou inválido: 401.
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	SecretKey jwtSecretKey(JwtProperties properties) {
		return new SecretKeySpec(Base64.getDecoder().decode(properties.secret()), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ofSeconds(5)),
				new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}

}
