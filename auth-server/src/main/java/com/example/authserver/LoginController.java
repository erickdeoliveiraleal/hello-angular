package com.example.authserver;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * Página de login do servidor de autorização: só o campo de nome. O formulário é enviado para o
 * POST /login padrão do Spring Security, que chama o {@link NameOnlyAuthenticationProvider}.
 */
@Controller
public class LoginController {

	@GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String login(CsrfToken csrf, @RequestParam(required = false) String error) {
		String errorMessage = error == null ? "" : "<p class=\"error\">Preencha o nome para entrar.</p>";
		return """
				<!doctype html>
				<html lang="pt-BR">
				<head>
				  <meta charset="utf-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1">
				  <title>Entrar - servidor de autorização</title>
				  <style>
				    body { font-family: sans-serif; max-width: 360px; margin: 64px auto; padding: 0 16px; }
				    form { display: flex; flex-direction: column; gap: 8px; }
				    input, button { padding: 8px; font-size: 1em; }
				    .error { color: #b00020; }
				    small { color: #555; }
				  </style>
				</head>
				<body>
				  <h1>Entrar</h1>
				  <small>Servidor de autorização (Spring Authorization Server)</small>
				  %s
				  <form method="post" action="/login">
				    <label for="username">Seu nome</label>
				    <input id="username" name="username" autofocus autocomplete="username">
				    <input type="hidden" name="%s" value="%s">
				    <button type="submit">Entrar</button>
				  </form>
				</body>
				</html>
				""".formatted(errorMessage, HtmlUtils.htmlEscape(csrf.getParameterName()),
				HtmlUtils.htmlEscape(csrf.getToken()));
	}

}
