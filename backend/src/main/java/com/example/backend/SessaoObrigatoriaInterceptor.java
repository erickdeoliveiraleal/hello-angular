package com.example.backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * "Segurança" de estudo: tudo em /api/protegido/** exige uma sessão com nome.
 * Sem ela, responde 401 antes de chegar ao controller.
 */
@Configuration
public class SessaoObrigatoriaInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		HttpSession sessao = request.getSession(false);
		if (sessao == null || sessao.getAttribute(SessaoController.ATRIBUTO_NOME) == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		return true;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/protegido/**");
	}

}
