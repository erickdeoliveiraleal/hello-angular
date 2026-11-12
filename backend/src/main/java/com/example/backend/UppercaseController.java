package com.example.backend;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A API: só aceita chamadas com um access token JWT do auth-server (Authorization: Bearer). */
@RestController
@RequestMapping("/api")
public class UppercaseController {

	public record NameRequest(String name) {
	}

	/** {@code user} é o "sub" do token: mostra de quem era o token que o BFF repassou. */
	public record NameResponse(String result, String user) {
	}

	@PostMapping("/uppercase")
	public NameResponse uppercase(@RequestBody NameRequest request, @AuthenticationPrincipal Jwt token) {
		String name = request.name() == null ? "" : request.name();
		return new NameResponse(name.toUpperCase(Locale.ROOT), token.getSubject());
	}

}
