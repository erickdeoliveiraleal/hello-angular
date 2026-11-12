package com.example.backend.auth;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class SecurityConfig {

	static final String TOKEN_TYPE_CLAIM = "token_type";
	static final String ACCESS = "access";
	static final String REFRESH = "refresh";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// API sem sessão nem cookies: o token vai no header Authorization, então CSRF não se aplica.
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/**", "/error").permitAll()
				.anyRequest().authenticated())
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

	/** Decoder usado pelo Spring Security nas requisições protegidas: só aceita access tokens. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
		return decoderFor(jwtSecretKey, properties, ACCESS);
	}

	static NimbusJwtDecoder decoderFor(SecretKey key, JwtProperties properties, String tokenType) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ofSeconds(5)),
				new JwtIssuerValidator(properties.issuer()),
				new JwtClaimValidator<String>(TOKEN_TYPE_CLAIM, tokenType::equals)));
		return decoder;
	}

}
