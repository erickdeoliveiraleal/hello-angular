package com.example.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.auth.TokenService.TokenResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public record LoginRequest(String name) {
	}

	public record RefreshRequest(String refreshToken) {
	}

	private final TokenService tokenService;

	public AuthController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	/** Login sem senha: basta informar um nome não vazio. */
	@PostMapping("/login")
	public TokenResponse login(@RequestBody LoginRequest request) {
		if (request.name() == null || request.name().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome");
		}
		return tokenService.issue(request.name().trim());
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@RequestBody RefreshRequest request) {
		if (request.refreshToken() == null || request.refreshToken().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token ausente");
		}
		return tokenService.refresh(request.refreshToken());
	}

}
