package com.example.backend.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.auth.TokenService.IssuedTokens;

/**
 * Login, refresh e logout. O access token volta no JSON; o refresh token só trafega num cookie
 * HttpOnly, que o JavaScript da página não consegue ler.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String REFRESH_COOKIE = "refresh_token";

	/**
	 * Proteção contra CSRF: o cookie é enviado automaticamente pelo navegador, mas um site de terceiros
	 * não consegue adicionar um header próprio sem passar pelo CORS. Somado ao SameSite=Strict.
	 */
	static final String CSRF_HEADER = "X-Requested-With";
	static final String CSRF_HEADER_VALUE = "XMLHttpRequest";

	public record LoginRequest(String name) {
	}

	public record AccessTokenResponse(String accessToken, long expiresIn, String user) {
	}

	private final TokenService tokenService;

	public AuthController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	/** Login sem senha: basta informar um nome não vazio. Se já havia uma sessão, ela é revogada. */
	@PostMapping("/login")
	public ResponseEntity<AccessTokenResponse> login(@RequestBody LoginRequest request,
			@RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
			@CookieValue(name = REFRESH_COOKIE, required = false) String currentRefreshToken) {
		requireCsrfHeader(csrfHeader);
		if (request.name() == null || request.name().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome");
		}
		if (currentRefreshToken != null) {
			tokenService.revoke(currentRefreshToken);
		}
		return withRefreshCookie(tokenService.issue(request.name().trim()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AccessTokenResponse> refresh(
			@RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		requireCsrfHeader(csrfHeader);
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sem sessão");
		}
		return withRefreshCookie(tokenService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		requireCsrfHeader(csrfHeader);
		if (refreshToken != null) {
			tokenService.revoke(refreshToken);
		}
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
			.build();
	}

	private ResponseEntity<AccessTokenResponse> withRefreshCookie(IssuedTokens tokens) {
		ResponseCookie cookie = refreshCookie(tokens.refreshToken(), tokenService.refreshTokenTtl());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(new AccessTokenResponse(tokens.accessToken(), tokens.expiresIn(), tokens.user()));
	}

	private static ResponseCookie refreshCookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)        // JavaScript não lê
			.secure(true)          // só via HTTPS (os navegadores aceitam também em http://localhost)
			.sameSite("Strict")    // não é enviado em requisições vindas de outros sites
			.path("/api/auth")     // só vai para os endpoints de autenticação
			.maxAge(maxAge)
			.build();
	}

	private static void requireCsrfHeader(String value) {
		if (!CSRF_HEADER_VALUE.equals(value)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Header " + CSRF_HEADER + " ausente");
		}
	}

}
