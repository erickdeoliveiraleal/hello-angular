package com.example.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Login e "quem sou eu". O logout é feito pelo próprio Spring Security (veja {@link SecurityConfig}). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public record LoginRequest(String name) {
	}

	public record UserResponse(String user) {
	}

	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy securityContextHolder = SecurityContextHolder
		.getContextHolderStrategy();

	public AuthController(SecurityContextRepository securityContextRepository) {
		this.securityContextRepository = securityContextRepository;
	}

	/** Login sem senha: basta um nome preenchido. O usuário passa a ficar guardado na sessão. */
	@PostMapping("/login")
	public UserResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		if (request.name() == null || request.name().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome");
		}
		String name = request.name().trim();

		// Proteção contra fixação de sessão: se já existia uma sessão, ela ganha um ID novo no login.
		if (httpRequest.getSession(false) != null) {
			httpRequest.changeSessionId();
		}

		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(name, null,
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = securityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);

		return new UserResponse(name);
	}

	/** Usado pelo front ao abrir a página (F5): responde quem está logado, ou 401 se não há sessão. */
	@GetMapping("/me")
	public UserResponse me(Authentication authentication) {
		return new UserResponse(authentication.getName());
	}

}
