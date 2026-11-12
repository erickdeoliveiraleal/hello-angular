package com.example.backend.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.auth.AuthServerClient.Tokens;

/**
 * Login, refresh e logout para o Angular. Os tokens são emitidos pelo auth-server; este controller só
 * os repassa: o access token no JSON e o refresh token num cookie HttpOnly (o JavaScript não lê).
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

	private final AuthServerClient authServer;
	private final JwtDecoder jwtDecoder;
	private final Duration refreshCookieMaxAge;

	public AuthController(AuthServerClient authServer, JwtDecoder jwtDecoder,
			@Value("${app.refresh-cookie-max-age}") Duration refreshCookieMaxAge) {
		this.authServer = authServer;
		this.jwtDecoder = jwtDecoder;
		this.refreshCookieMaxAge = refreshCookieMaxAge;
	}

	/** Login sem senha: o nome vai para o auth-server (grant customizado), que devolve os tokens. */
	@PostMapping("/login")
	public ResponseEntity<AccessTokenResponse> login(@RequestBody LoginRequest request,
			@RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
			@CookieValue(name = REFRESH_COOKIE, required = false) String currentRefreshToken) {
		requireCsrfHeader(csrfHeader);
		if (request.name() == null || request.name().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome");
		}
		if (currentRefreshToken != null) {
			authServer.revoke(currentRefreshToken);
		}
		return withRefreshCookie(authServer.tokensForName(request.name().trim()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AccessTokenResponse> refresh(
			@RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		requireCsrfHeader(csrfHeader);
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sem sessão");
		}
		return withRefreshCookie(authServer.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		requireCsrfHeader(csrfHeader);
		if (refreshToken != null) {
			authServer.revoke(refreshToken);
		}
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
			.build();
	}

	private ResponseEntity<AccessTokenResponse> withRefreshCookie(Tokens tokens) {
		// O nome do usuário é o "sub" do access token (validado com a chave pública do auth-server).
		String user = jwtDecoder.decode(tokens.accessToken()).getSubject();
		ResponseCookie cookie = refreshCookie(tokens.refreshToken(), refreshCookieMaxAge);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(new AccessTokenResponse(tokens.accessToken(), tokens.expiresIn(), user));
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
