package com.example.backend;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

/**
 * Ações que dependem de permissão. Esta é a segurança de verdade: esconder o botão na
 * tela é só aparência, porque qualquer um pode chamar a URL direto (pelo F12, por exemplo).
 *
 * O @PreAuthorize é conferido antes de o método rodar; sem a permissão, a resposta é 403
 * e o método nem executa. É o equivalente ao @RolesAllowed do Jakarta EE.
 */
@RestController
@RequestMapping("/api")
public class OperacoesController {

	public record ResultadoResponse(String mensagem) {
	}

	@PostMapping("/visitas")
	@PreAuthorize("hasAuthority('VISITA_REGISTRAR')")
	public ResultadoResponse registrarVisita(Authentication usuario, HttpSession sessao) {
		return new ResultadoResponse("Visita registrada por " + usuario.getName() + " no posto "
				+ sessao.getAttribute(SessaoController.ATRIBUTO_POSTO) + ".");
	}

	@PostMapping("/escala")
	@PreAuthorize("hasAuthority('ESCALA_EDITAR')")
	public ResultadoResponse editarEscala(Authentication usuario, HttpSession sessao) {
		return new ResultadoResponse("Escala editada por " + usuario.getName() + " no posto "
				+ sessao.getAttribute(SessaoController.ATRIBUTO_POSTO) + ".");
	}

}
