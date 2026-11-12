package com.example.backend.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma "linha da tabela" de refresh tokens. Guarda só o hash do token, nunca o token em si.
 *
 * @param tokenHash SHA-256 (hex) do refresh token
 * @param user      usuário dono da sessão
 * @param family    cadeia de refreshes de um mesmo login; reuso revoga a família inteira
 * @param expiresAt quando o token deixa de valer
 * @param usedAt    quando foi trocado por um novo (null = ainda não usado)
 * @param revokedAt quando foi revogado por logout ou reuso (null = não revogado)
 */
public record RefreshSession(String tokenHash, String user, UUID family, Instant expiresAt, Instant usedAt,
		Instant revokedAt) {

	RefreshSession withUsedAt(Instant instant) {
		return new RefreshSession(tokenHash, user, family, expiresAt, instant, revokedAt);
	}

	RefreshSession withRevokedAt(Instant instant) {
		return new RefreshSession(tokenHash, user, family, expiresAt, usedAt, instant);
	}

}
