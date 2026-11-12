package com.example.authserver;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Login sem senha: qualquer nome preenchido é aceito. Num sistema real, aqui entraria a verificação
 * de senha (ou de outro fator) contra a base de usuários.
 */
public class NameOnlyAuthenticationProvider implements AuthenticationProvider {

	@Override
	public Authentication authenticate(Authentication authentication) {
		String name = authentication.getName() == null ? "" : authentication.getName().trim();
		if (name.isEmpty()) {
			throw new BadCredentialsException("Informe um nome");
		}
		return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of(
				new SimpleGrantedAuthority("ROLE_USER"),
				// No Spring Security 7, cada fator de login fica registrado com o horário em que foi feito.
				// O servidor de autorização usa esse horário no "auth_time" do ID token. O formulário de
				// nome faz o papel do fator "senha".
				FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

}
