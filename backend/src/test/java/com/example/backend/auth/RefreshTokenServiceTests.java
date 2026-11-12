package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.auth.RefreshTokenService.Rotation;

/** Testes de unidade, sem Spring: o relógio é controlado para testar a expiração. */
class RefreshTokenServiceTests {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
	private InMemoryRefreshTokenStore store;
	private RefreshTokenService service;

	@BeforeEach
	void setUp() {
		store = new InMemoryRefreshTokenStore();
		JwtProperties properties = new JwtProperties(null, null, null, Duration.ofDays(1));
		service = new RefreshTokenService(store, properties, clock);
	}

	@Test
	void guardaSoOHashDoToken() {
		String token = service.create("joão");

		assertThat(store.findByHash(token)).isEmpty();
		RefreshSession session = store.findByHash(RefreshTokenService.hash(token)).orElseThrow();
		assertThat(session.tokenHash()).hasSize(64).isNotEqualTo(token);
		assertThat(session.user()).isEqualTo("joão");
	}

	@Test
	void rotacaoDevolveTokenNovoDaMesmaFamilia() {
		String token = service.create("joão");

		Rotation rotation = service.rotate(token);

		assertThat(rotation.user()).isEqualTo("joão");
		assertThat(rotation.refreshToken()).isNotEqualTo(token);
		assertThat(familyOf(rotation.refreshToken())).isEqualTo(familyOf(token));
	}

	@Test
	void reusoRevogaAFamiliaInteira() {
		String first = service.create("joão");
		String second = service.rotate(first).refreshToken();

		assertUnauthorized(() -> service.rotate(first), "reutilizado");
		assertUnauthorized(() -> service.rotate(second), "revogado");
	}

	@Test
	void reusoNaoAfetaOutrasFamilias() {
		String joao = service.create("joão");
		String maria = service.create("maria");
		service.rotate(joao);

		assertUnauthorized(() -> service.rotate(joao), "reutilizado");
		assertThat(service.rotate(maria).user()).isEqualTo("maria");
	}

	@Test
	void logoutRevogaAFamilia() {
		String first = service.create("joão");
		String second = service.rotate(first).refreshToken();

		service.revoke(second);

		assertUnauthorized(() -> service.rotate(second), "revogado");
	}

	@Test
	void tokenExpiradoEhRecusado() {
		String token = service.create("joão");
		clock.advance(Duration.ofDays(1));

		assertUnauthorized(() -> service.rotate(token), "expirado");
	}

	@Test
	void tokenDesconhecidoEhRecusado() {
		assertUnauthorized(() -> service.rotate("token-inventado"), "desconhecido");
	}

	@Test
	void limpezaApagaSoOsExpirados() {
		service.create("joão");
		clock.advance(Duration.ofHours(12));
		String recent = service.create("maria");
		clock.advance(Duration.ofHours(13));

		service.deleteExpired();

		assertThat(store.size()).isEqualTo(1);
		assertThat(store.findByHash(RefreshTokenService.hash(recent))).isPresent();
	}

	private Object familyOf(String token) {
		return store.findByHash(RefreshTokenService.hash(token)).orElseThrow().family();
	}

	private static void assertUnauthorized(Runnable action, String reasonFragment) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(ResponseStatusException.class,
					ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401))
			.hasMessageContaining(reasonFragment);
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
