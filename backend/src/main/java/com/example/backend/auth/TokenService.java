package com.example.backend.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TokenService {

	/** Par de tokens emitido para um usuário. O refresh token vai em cookie, nunca no JSON. */
	public record IssuedTokens(String user, String accessToken, String refreshToken, long expiresIn) {
	}

	private final JwtEncoder encoder;
	private final JwtDecoder refreshDecoder;
	private final JwtProperties properties;

	/**
	 * Refresh tokens já usados ou revogados no logout (jti -> expiração): cada refresh token vale uma vez só.
	 * Fica em memória, então é zerado quando o backend reinicia.
	 */
	private final Map<String, Instant> revokedRefreshTokens = new ConcurrentHashMap<>();

	public TokenService(JwtEncoder encoder, SecretKey jwtSecretKey, JwtProperties properties) {
		this.encoder = encoder;
		this.refreshDecoder = SecurityConfig.decoderFor(jwtSecretKey, properties, SecurityConfig.REFRESH);
		this.properties = properties;
	}

	public IssuedTokens issue(String subject) {
		Instant now = Instant.now();
		String accessToken = encode(subject, SecurityConfig.ACCESS, now, now.plus(properties.accessTokenTtl()));
		String refreshToken = encode(subject, SecurityConfig.REFRESH, now, now.plus(properties.refreshTokenTtl()));
		return new IssuedTokens(subject, accessToken, refreshToken, properties.accessTokenTtl().toSeconds());
	}

	public IssuedTokens refresh(String refreshToken) {
		Jwt jwt = decodeRefresh(refreshToken);
		if (jwt == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou expirado");
		}
		if (!markRevoked(jwt)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token já utilizado ou revogado");
		}
		return issue(jwt.getSubject());
	}

	/** Invalida o refresh token (logout ou troca de usuário). Token inválido é ignorado. */
	public void revoke(String refreshToken) {
		Jwt jwt = decodeRefresh(refreshToken);
		if (jwt != null) {
			markRevoked(jwt);
		}
	}

	public Duration refreshTokenTtl() {
		return properties.refreshTokenTtl();
	}

	private Jwt decodeRefresh(String refreshToken) {
		try {
			return refreshDecoder.decode(refreshToken);
		}
		catch (JwtException ex) {
			return null;
		}
	}

	/** @return {@code false} se o token já estava usado ou revogado. */
	private boolean markRevoked(Jwt jwt) {
		Instant now = Instant.now();
		revokedRefreshTokens.values().removeIf(expiresAt -> expiresAt.isBefore(now));
		return revokedRefreshTokens.putIfAbsent(jwt.getId(), jwt.getExpiresAt()) == null;
	}

	String encode(String subject, String tokenType, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(subject)
			.id(UUID.randomUUID().toString())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim(SecurityConfig.TOKEN_TYPE_CLAIM, tokenType)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
