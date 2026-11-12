package com.example.backend;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Os postos de trabalho e o que cada um permite fazer. Fixo no código, só para estudo;
 * num sistema real viria do banco (lotação do servidor, perfis do posto...).
 *
 * A ideia: o usuário escolhe em qual posto está trabalhando, e as permissões dele
 * passam a ser as do posto. Trocar de posto troca as permissões na hora.
 */
@Component
public class PostosDeTrabalho {

	public static final String VISITA_REGISTRAR = "VISITA_REGISTRAR";
	public static final String ESCALA_EDITAR = "ESCALA_EDITAR";

	/** O que cada posto pode fazer. */
	private static final Map<String, Set<String>> PERMISSOES_DO_POSTO = Map.of(
			"PORTARIA", Set.of(VISITA_REGISTRAR),
			"CHEFIA", Set.of(VISITA_REGISTRAR, ESCALA_EDITAR));

	/** Em quais postos cada usuário pode trabalhar; o primeiro é o posto inicial. */
	private static final Map<String, List<String>> POSTOS_DO_USUARIO = Map.of(
			"erick", List.of("PORTARIA", "CHEFIA"));

	public List<String> postosDe(String usuario) {
		return POSTOS_DO_USUARIO.getOrDefault(usuario, List.of());
	}

	public boolean podeTrabalharEm(String usuario, String posto) {
		return postosDe(usuario).contains(posto);
	}

	/**
	 * O usuário autenticado com as permissões do posto. As permissões viram
	 * "authorities" do Spring Security, que é o que o @PreAuthorize confere.
	 */
	public Authentication autenticadoNoPosto(String usuario, String posto) {
		List<SimpleGrantedAuthority> permissoes = PERMISSOES_DO_POSTO.getOrDefault(posto, Set.of()).stream()
			.sorted()
			.map(SimpleGrantedAuthority::new)
			.toList();
		return UsernamePasswordAuthenticationToken.authenticated(usuario, null, permissoes);
	}

}
