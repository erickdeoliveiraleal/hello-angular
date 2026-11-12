package com.example.backend;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Login "de estudo" com a sessão HTTP do servidor (cookie JSESSIONID).
 * Não há senha: quem digitar um nome fica logado com ele.
 */
@RestController
@RequestMapping("/api")
public class SessaoController {

	static final String ATRIBUTO_NOME = "nome";
	static final String ATRIBUTO_HORA_LOGIN = "horaLogin";

	public record LoginRequest(String name) {
	}

	public record UsuarioResponse(String name) {
	}

	/** Cria a sessão (o Tomcat devolve o cookie JSESSIONID) e guarda o nome nela. */
	@PostMapping("/login")
	public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
		String nome = request.name() == null ? "" : request.name().trim();
		if (nome.isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		HttpSession antiga = http.getSession(false);
		if (antiga != null) {
			antiga.invalidate();
		}
		HttpSession sessao = http.getSession(true);
		sessao.setAttribute(ATRIBUTO_NOME, nome);
		sessao.setAttribute(ATRIBUTO_HORA_LOGIN, Instant.now());
		return ResponseEntity.ok(new UsuarioResponse(nome));
	}

	/** Diz quem está logado nesta sessão; 401 se não houver sessão. */
	@GetMapping("/me")
	public ResponseEntity<UsuarioResponse> me(HttpServletRequest http) {
		HttpSession sessao = http.getSession(false); // false: não cria sessão vazia
		Object nome = sessao == null ? null : sessao.getAttribute(ATRIBUTO_NOME);
		if (nome == null) {
			return ResponseEntity.status(401).build();
		}
		return ResponseEntity.ok(new UsuarioResponse((String) nome));
	}

	/** Encerra a sessão no servidor. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest http) {
		HttpSession sessao = http.getSession(false);
		if (sessao != null) {
			sessao.invalidate();
		}
		return ResponseEntity.noContent().build();
	}

}
