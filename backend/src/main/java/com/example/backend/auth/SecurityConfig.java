package com.example.backend.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Autenticação por sessão no servidor (JSESSIONID). O usuário logado fica guardado na HttpSession;
 * o navegador só tem o ID da sessão, num cookie HttpOnly.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * Chain do navegador. É a {@code @Order(2)}: pega tudo o que a chain do app móvel
	 * ({@link MobileSecurityConfig}, Bearer token) não pegou.
	 */
	@Bean
	@Order(2)
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
			throws Exception {
		http
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			// O cookie de sessão vai sozinho em toda requisição, então é preciso proteção contra CSRF.
			// spa(): o token vai num cookie XSRF-TOKEN legível pelo Angular, que o devolve no header
			// X-XSRF-TOKEN. Um site de terceiros não consegue ler esse cookie nem enviar o header.
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			// Não guarda a requisição negada na sessão (útil só em sites com página de login e redirecionamento).
			// Sem isso, um simples 401 já criaria uma sessão vazia.
			.requestCache(cache -> cache.disable())
			// Sem sessão: 401 (e não um redirecionamento para uma página de login).
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			// POST /api/auth/logout invalida a sessão no servidor e apaga o cookie.
			.logout(logout -> logout
				.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
				.deleteCookies("JSESSIONID"));
		return http.build();
	}

}
