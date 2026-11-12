package com.example.backend.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Onde os refresh tokens ficam guardados. Hoje a implementação é em memória
 * ({@link InMemoryRefreshTokenStore}); trocar por banco ou Redis é criar outra implementação.
 */
public interface RefreshTokenStore {

	void save(RefreshSession session);

	Optional<RefreshSession> findByHash(String tokenHash);

	/**
	 * Marca o token como usado, de forma atômica.
	 * @return {@code false} se ele já estava usado (reuso) ou não existe
	 */
	boolean markUsed(String tokenHash, Instant usedAt);

	/** Revoga todos os tokens da família (logout ou reuso detectado). */
	void revokeFamily(UUID family, Instant revokedAt);

	/** Apaga os tokens expirados. @return quantos foram apagados */
	int deleteExpired(Instant now);

}
