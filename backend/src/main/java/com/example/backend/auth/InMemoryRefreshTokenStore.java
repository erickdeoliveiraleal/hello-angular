package com.example.backend.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Simula a tabela de refresh tokens com um mapa em memória (hash -> sessão).
 * Tudo se perde quando o backend reinicia: os usuários precisam fazer login de novo.
 */
@Component
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

	private final Map<String, RefreshSession> sessions = new ConcurrentHashMap<>();

	@Override
	public void save(RefreshSession session) {
		sessions.put(session.tokenHash(), session);
	}

	@Override
	public Optional<RefreshSession> findByHash(String tokenHash) {
		return Optional.ofNullable(sessions.get(tokenHash));
	}

	@Override
	public boolean markUsed(String tokenHash, Instant usedAt) {
		AtomicBoolean marked = new AtomicBoolean(false);
		sessions.computeIfPresent(tokenHash, (hash, session) -> {
			if (session.usedAt() != null) {
				return session;
			}
			marked.set(true);
			return session.withUsedAt(usedAt);
		});
		return marked.get();
	}

	@Override
	public void revokeFamily(UUID family, Instant revokedAt) {
		sessions.replaceAll((hash, session) -> session.family().equals(family) && session.revokedAt() == null
				? session.withRevokedAt(revokedAt) : session);
	}

	@Override
	public int deleteExpired(Instant now) {
		int before = sessions.size();
		sessions.values().removeIf(session -> !session.expiresAt().isAfter(now));
		return before - sessions.size();
	}

	int size() {
		return sessions.size();
	}

}
