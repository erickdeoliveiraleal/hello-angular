package com.example.backend;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class MensagemController {

	public record MensagemResponse(String mensagem) {
	}

	public record HoraLoginResponse(String name, Instant horaLogin) {
	}

	/** Desprotegido: responde mesmo sem sessão. */
	@GetMapping("/publico/mensagem")
	public MensagemResponse mensagem() {
		return new MensagemResponse("Olá do backend! Esta mensagem é pública, não precisa de login.");
	}

	/**
	 * Protegido: o {@link SessaoObrigatoriaInterceptor} barra com 401 quem não
	 * tem sessão, então aqui a sessão sempre existe.
	 */
	@GetMapping("/protegido/hora-login")
	public HoraLoginResponse horaLogin(HttpServletRequest http) {
		var sessao = http.getSession(false);
		return new HoraLoginResponse(
				(String) sessao.getAttribute(SessaoController.ATRIBUTO_NOME),
				(Instant) sessao.getAttribute(SessaoController.ATRIBUTO_HORA_LOGIN));
	}

}
