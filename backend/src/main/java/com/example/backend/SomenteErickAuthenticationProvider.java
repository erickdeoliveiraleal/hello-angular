package com.example.backend;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Decide quem pode entrar. É o lugar do Spring Security para essa regra, parecido
 * com um IdentityStore do Jakarta Security ou um security realm do WildFly (Elytron):
 * recebe as credenciais e devolve um usuário autenticado, ou lança exceção.
 *
 * Não há senha: só o nome "erick" entra (maiúsculas e minúsculas não importam).
 */
@Component
public class SomenteErickAuthenticationProvider implements AuthenticationProvider {

	static final String USUARIO_PERMITIDO = "erick";

	@Override
	public Authentication authenticate(Authentication tentativa) {
		String nome = tentativa.getName().trim();
		if (!nome.equalsIgnoreCase(USUARIO_PERMITIDO)) {
			throw new BadCredentialsException("Usuário não autorizado");
		}
		// Autenticado, sem nenhum perfil (role).
		return UsernamePasswordAuthenticationToken.authenticated(nome, null, List.of());
	}

	/** Este provider só trata tentativas de login por nome (usuário/senha). */
	@Override
	public boolean supports(Class<?> tipo) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
	}

}
