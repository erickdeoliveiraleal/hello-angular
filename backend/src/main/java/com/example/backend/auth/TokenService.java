package com.example.backend.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.backend.auth.RefreshTokenService.Rotation;

/**
 * Emite o par de tokens: access token JWT (validado só pela assinatura, sem consultar nada)
 * e refresh token opaco (string aleatória, validada pelo {@link RefreshTokenService}).
 */
@Service
public class TokenService {

	/** Par de tokens emitido para um usuário. O refresh token vai em cookie, nunca no JSON. */
	public record IssuedTokens(String user, String accessToken, String refreshToken, long expiresIn) {
	}

	private final JwtEncoder encoder;
	private final RefreshTokenService refreshTokens;
	private final JwtProperties properties;

	public TokenService(JwtEncoder encoder, RefreshTokenService refreshTokens, JwtProperties properties) {
		this.encoder = encoder;
		this.refreshTokens = refreshTokens;
		this.properties = properties;
	}

	/** Login: access token novo e uma família nova de refresh tokens. */
	public IssuedTokens issue(String user) {
		return tokensFor(user, refreshTokens.create(user));
	}

	/** Refresh: gira o refresh token (mesma família) e emite um access token novo. */
	public IssuedTokens refresh(String refreshToken) {
		Rotation rotation = refreshTokens.rotate(refreshToken);
		return tokensFor(rotation.user(), rotation.refreshToken());
	}

	/** Logout ou troca de usuário. */
	public void revoke(String refreshToken) {
		refreshTokens.revoke(refreshToken);
	}

	public Duration refreshTokenTtl() {
		return refreshTokens.ttl();
	}

	private IssuedTokens tokensFor(String user, String refreshToken) {
		Instant now = Instant.now();
		String accessToken = encodeAccessToken(user, now, now.plus(properties.accessTokenTtl()));
		return new IssuedTokens(user, accessToken, refreshToken, properties.accessTokenTtl().toSeconds());
	}

	String encodeAccessToken(String subject, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(subject)
			.id(UUID.randomUUID().toString())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim(SecurityConfig.TOKEN_TYPE_CLAIM, SecurityConfig.ACCESS)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
