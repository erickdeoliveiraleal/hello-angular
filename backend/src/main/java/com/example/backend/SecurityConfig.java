package com.example.backend;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Regras de acesso num lugar só, no lugar do antigo SessaoObrigatoriaInterceptor.
 * Parecido com os {@code <security-constraint>} do web.xml: o Spring Security é uma
 * cadeia de filtros de servlet que roda antes dos controllers.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/login", "/api/publico/**", "/error").permitAll()
				.anyRequest().authenticated())
			// Sem login: 401 (o padrão seria redirecionar para uma página de login, como no JSF).
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			// O próprio Spring invalida a sessão e responde 204.
			.logout(logout -> logout
				.logoutUrl("/api/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// CSRF: o JSF se protege com o ViewState; aqui o Spring grava um token no cookie
			// XSRF-TOKEN e exige o mesmo valor no header X-XSRF-TOKEN em todo POST/PUT/DELETE.
			// O HttpClient do Angular lê o cookie e envia o header sozinho.
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
		return http.build();
	}

	/**
	 * Quem o SessaoController chama para autenticar. Ele repassa a tentativa ao provider,
	 * que decide; para trocar a regra (por exemplo, exigir senha), só se troca o provider.
	 */
	@Bean
	AuthenticationManager authenticationManager(SomenteErickAuthenticationProvider provider) {
		return new ProviderManager(provider);
	}

	/**
	 * O Spring só grava o cookie XSRF-TOKEN quando alguém lê o token. Este filtro lê em toda
	 * requisição, para o navegador já ter o cookie antes do primeiro POST (o login).
	 */
	static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}

	}

}
