package com.example.backend;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class MensagemController {

	public record MensagemResponse(String mensagem) {
	}

	public record HoraLoginResponse(String name, Instant horaLogin) {
	}

	/** Público: liberado em {@link SecurityConfig}, responde mesmo sem sessão. */
	@GetMapping("/publico/mensagem")
	public MensagemResponse mensagem() {
		return new MensagemResponse("Olá do backend! Esta mensagem é pública, não precisa de login.");
	}

	/**
	 * Protegido: o Spring Security barra com 401 quem não está logado, então aqui
	 * o usuário e a sessão sempre existem.
	 */
	@GetMapping("/protegido/hora-login")
	public HoraLoginResponse horaLogin(Authentication usuario, HttpSession sessao) {
		return new HoraLoginResponse(usuario.getName(),
				(Instant) sessao.getAttribute(SessaoController.ATRIBUTO_HORA_LOGIN));
	}

}
