package com.example.backend;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
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
 * Login com usuário e senha, guardado na sessão HTTP do servidor (cookie JSESSIONID),
 * e o posto de trabalho em que o usuário está.
 *
 * O controller não decide quem entra: ele pede ao AuthenticationManager do Spring
 * Security, que confere usuário e senha (veja {@link SecurityConfig} e
 * {@link UsuariosConfig}). As permissões vêm do posto (veja {@link PostosDeTrabalho}).
 * Tudo fica guardado na HttpSession. O logout é o do próprio Spring.
 */
@RestController
@RequestMapping("/api")
public class SessaoController {

	static final String ATRIBUTO_HORA_LOGIN = "horaLogin";
	static final String ATRIBUTO_POSTO = "postoTrabalho";

	public record LoginRequest(String name, String password) {
	}

	public record TrocaDePostoRequest(String posto) {
	}

	/** Tudo o que a tela precisa saber sobre quem está logado, inclusive para mostrar ou esconder botões. */
	public record UsuarioResponse(String name, String postoTrabalho, List<String> postosDisponiveis,
			List<String> permissoes) {
	}

	private final AuthenticationManager authenticationManager;

	private final PostosDeTrabalho postos;

	/** Guarda o usuário autenticado dentro da HttpSession. */
	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	public SessaoController(AuthenticationManager authenticationManager, PostosDeTrabalho postos) {
		this.authenticationManager = authenticationManager;
		this.postos = postos;
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

		// Começa no primeiro posto do usuário (ou em nenhum, se ele não tiver posto).
		List<String> postosDoUsuario = postos.postosDe(usuario.getName());
		String postoInicial = postosDoUsuario.isEmpty() ? null : postosDoUsuario.get(0);
		Authentication noPosto = entrarNoPosto(usuario.getName(), postoInicial, http, response);

		return ResponseEntity.ok(resposta(noPosto, sessao));
	}

	/** Quem está logado. Sem sessão o Spring Security já responde 401 antes de chegar aqui. */
	@GetMapping("/me")
	public UsuarioResponse me(Authentication usuario, HttpSession sessao) {
		return resposta(usuario, sessao);
	}

	/**
	 * Troca o posto de trabalho. As permissões passam a ser as do novo posto já na próxima
	 * requisição, porque ficam na sessão do servidor (com JWT, ficariam presas no token
	 * até ele expirar).
	 */
	@PostMapping("/posto")
	public ResponseEntity<UsuarioResponse> trocarPosto(@RequestBody TrocaDePostoRequest request,
			Authentication usuario, HttpServletRequest http, HttpServletResponse response) {
		if (!postos.podeTrabalharEm(usuario.getName(), request.posto())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		// As permissões mudaram: troca o id da sessão, pelo mesmo motivo do login.
		http.changeSessionId();
		Authentication noPosto = entrarNoPosto(usuario.getName(), request.posto(), http, response);
		return ResponseEntity.ok(resposta(noPosto, http.getSession(false)));
	}

	/** Guarda na sessão o posto e o usuário com as permissões desse posto. */
	private Authentication entrarNoPosto(String usuario, String posto, HttpServletRequest http,
			HttpServletResponse response) {
		Authentication noPosto = postos.autenticadoNoPosto(usuario, posto);
		http.getSession(false).setAttribute(ATRIBUTO_POSTO, posto);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(noPosto);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, http, response);
		return noPosto;
	}

	private UsuarioResponse resposta(Authentication usuario, HttpSession sessao) {
		List<String> permissoes = usuario.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.sorted()
			.toList();
		return new UsuarioResponse(usuario.getName(), (String) sessao.getAttribute(ATRIBUTO_POSTO),
				postos.postosDe(usuario.getName()), permissoes);
	}

}
