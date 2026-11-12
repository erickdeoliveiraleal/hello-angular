package com.example.backend;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Login com usuário e senha, guardado na sessão HTTP do servidor (cookie JSESSIONID).
 *
 * O controller não decide quem entra: ele pede ao AuthenticationManager do Spring
 * Security, que confere usuário e senha (veja {@link SecurityConfig} e
 * {@link UsuariosConfig}). O usuário autenticado fica guardado na HttpSession. O logout
 * é o do próprio Spring.
 */
@RestController
@RequestMapping("/api")
public class SessaoController {

	static final String ATRIBUTO_HORA_LOGIN = "horaLogin";

	public record LoginRequest(String name, String password) {
	}

	public record UsuarioResponse(String name) {
	}

	private final AuthenticationManager authenticationManager;

	/** Guarda o usuário autenticado dentro da HttpSession. */
	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	public SessaoController(AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/login")
	public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request, HttpServletRequest http,
			HttpServletResponse response) {
		String nome = request.name() == null ? "" : request.name().trim();
		// A senha não é "limpa": espaços fazem parte dela.
		String senha = request.password() == null ? "" : request.password();
		if (nome.isEmpty() || senha.isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		// Quem decide é o Spring Security. Usuário inexistente e senha errada viram a
		// mesma exceção (BadCredentialsException) e o mesmo 401: quem tenta adivinhar
		// não descobre quais usuários existem.
		Authentication usuario;
		try {
			usuario = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(nome, senha));
		}
		catch (AuthenticationException recusado) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		// Sessão nova a cada login (evita "session fixation").
		HttpSession antiga = http.getSession(false);
		if (antiga != null) {
			antiga.invalidate();
		}
		HttpSession sessao = http.getSession(true);
		sessao.setAttribute(ATRIBUTO_HORA_LOGIN, Instant.now());

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(usuario);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, http, response);

		return ResponseEntity.ok(new UsuarioResponse(usuario.getName()));
	}

	/** Quem está logado. Sem sessão o Spring Security já responde 401 antes de chegar aqui. */
	@GetMapping("/me")
	public UsuarioResponse me(Authentication usuario) {
		return new UsuarioResponse(usuario.getName());
	}

}
