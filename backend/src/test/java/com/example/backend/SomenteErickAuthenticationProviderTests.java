package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Teste de unidade puro: o provider é uma classe Java comum, sem precisar subir o Spring. */
class SomenteErickAuthenticationProviderTests {

	private final SomenteErickAuthenticationProvider provider = new SomenteErickAuthenticationProvider();

	@Test
	void aceitaErickSemDiferenciarMaiusculas() {
		Authentication usuario = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(" Erick ", null));

		assertThat(usuario.isAuthenticated()).isTrue();
		assertThat(usuario.getName()).isEqualTo("Erick");
	}

	@Test
	void recusaOutroNome() {
		assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("Ana", null)))
			.isInstanceOf(BadCredentialsException.class);
	}

}
