package com.example.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Refresh tokens opacos: 32 bytes aleatórios, sem conteúdo legível. O servidor guarda só o hash
 * SHA-256 e decide se o token vale consultando o {@link RefreshTokenStore} (lista de permitidos).
 */
@Service
public class RefreshTokenService {

	/** Resultado de uma rotação: de quem é a sessão e o refresh token novo. */
	public record Rotation(String user, String refreshToken) {
	}

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenStore store;
	private final Duration ttl;
	private final Clock clock;

	public RefreshTokenService(RefreshTokenStore store, JwtProperties properties, Clock clock) {
		this.store = store;
		this.ttl = properties.refreshTokenTtl();
		this.clock = clock;
	}

	/** Novo login: abre uma família nova. */
	public String create(String user) {
		return create(user, UUID.randomUUID());
	}

	/**
	 * Troca o refresh token por um novo da mesma família. Se o token já tinha sido usado,
	 * é sinal de roubo (o cliente legítimo já tinha trocado por outro): a família inteira é revogada.
	 */
	public Rotation rotate(String refreshToken) {
		Instant now = clock.instant();
		RefreshSession session = store.findByHash(hash(refreshToken))
			.orElseThrow(() -> unauthorized("Refresh token desconhecido"));

		if (session.revokedAt() != null) {
			throw unauthorized("Refresh token revogado");
		}
		if (!session.expiresAt().isAfter(now)) {
			throw unauthorized("Refresh token expirado");
		}
		if (!store.markUsed(session.tokenHash(), now)) {
			store.revokeFamily(session.family(), now);
			log.warn("Reuso de refresh token detectado para o usuário '{}': família {} revogada", session.user(),
					session.family());
			throw unauthorized("Refresh token reutilizado; sessão encerrada");
		}
		return new Rotation(session.user(), create(session.user(), session.family()));
	}

	/** Logout: revoga a família do token (todos os refreshes daquele login). Token desconhecido é ignorado. */
	public void revoke(String refreshToken) {
		store.findByHash(hash(refreshToken)).ifPresent(session -> store.revokeFamily(session.family(), clock.instant()));
	}

	public Duration ttl() {
		return ttl;
	}

	/** Limpeza periódica, o equivalente a um DELETE ... WHERE expira_em < now() no banco. */
	@Scheduled(fixedDelayString = "${app.jwt.refresh-cleanup-interval:PT10M}")
	public void deleteExpired() {
		int removed = store.deleteExpired(clock.instant());
		if (removed > 0) {
			log.debug("{} refresh tokens expirados removidos", removed);
		}
	}

	private String create(String user, UUID family) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		store.save(new RefreshSession(hash(token), user, family, clock.instant().plus(ttl), null, null));
		return token;
	}

	/**
	 * SHA-256 basta (não precisa de bcrypt): o token tem 256 bits aleatórios, então não existe
	 * ataque de dicionário como contra senhas.
	 */
	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível", ex);
		}
	}

	private static ResponseStatusException unauthorized(String reason) {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
	}

}
