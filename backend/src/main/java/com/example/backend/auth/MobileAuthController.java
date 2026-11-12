package com.example.backend.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Login do app móvel: troca o nome por um access token JWT, que volta no JSON (não há cookie).
 * O app guarda o token (no Android, no Keystore) e o manda em {@code Authorization: Bearer ...}.
 *
 * <p>Versão mínima para demonstração: só access token, sem refresh token nem logout. Um app de verdade
 * precisaria de um refresh token (como o da branch {@code jwt-manual}, mas devolvido no JSON).
 */
@RestController
@RequestMapping("/api/mobile")
public class MobileAuthController {

	public record TokenRequest(String name) {
	}

	public record TokenResponse(String accessToken, String tokenType, long expiresIn, String user) {
	}

	private final JwtEncoder encoder;
	private final JwtProperties properties;

	public MobileAuthController(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	@PostMapping("/token")
	public TokenResponse token(@RequestBody TokenRequest request) {
		if (request.name() == null || request.name().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome");
		}
		String name = request.name().trim();
		Instant now = Instant.now();
		String token = encode(name, now, now.plus(properties.accessTokenTtl()));
		return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds(), name);
	}

	String encode(String subject, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(subject)
			.id(UUID.randomUUID().toString())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
