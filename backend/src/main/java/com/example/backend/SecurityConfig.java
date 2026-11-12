package com.example.backend;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.NullRequestCache;
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
// Liga o @PreAuthorize nos métodos dos controllers (veja OperacoesController).
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// Onde fica o token CSRF: no cookie XSRF-TOKEN, legível pelo JavaScript (é o que o
		// csrf.spa() usaria). Fica numa variável porque o logout também precisa dele.
		CookieCsrfTokenRepository tokensCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/login", "/api/publico/**", "/error").permitAll()
				// Toda a API exige login, inclusive endpoints que ainda vão ser criados.
				.requestMatchers("/api/**").authenticated()
				// Fora de /api só há os arquivos da tela (index.html, .js, .css), servidos
				// pelo próprio Spring no .jar de produção. A tela é pública; os dados, não.
				.anyRequest().permitAll())
			// Sem login: 401 (o padrão seria redirecionar para uma página de login, como no JSF).
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			// Por padrão o Spring guarda na sessão a requisição recusada, para "voltar a ela"
			// depois do login. Isso só serve para sites com redirecionamento; numa API, só
			// criaria uma sessão à toa para cada visitante sem login.
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			// O próprio Spring invalida a sessão, apaga o token CSRF e responde 204.
			.logout(logout -> logout
				.logoutUrl("/api/logout")
				.logoutSuccessHandler((request, response, autenticacao) -> {
					// O logout apaga o token CSRF (um token por "sessão de uso"). Sem um novo,
					// o próximo POST da tela, como entrar de novo sem recarregar, levaria 403.
					tokensCsrf.saveToken(tokensCsrf.generateToken(request), request, response);
					response.setStatus(HttpStatus.NO_CONTENT.value());
				}))
			// CSRF: o JSF se protege com o ViewState; aqui o Spring grava um token no cookie
			// XSRF-TOKEN e exige o mesmo valor no header X-XSRF-TOKEN em todo POST/PUT/DELETE.
			// O HttpClient do Angular lê o cookie e envia o header sozinho.
			.csrf(csrf -> csrf.spa().csrfTokenRepository(tokensCsrf))
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
		return http.build();
	}

	/**
	 * Quem o SessaoController chama para autenticar. Ele repassa a tentativa ao provider.
	 *
	 * O DaoAuthenticationProvider é o provider padrão do Spring para usuário e senha:
	 * busca o usuário no UserDetailsService e confere a senha com o PasswordEncoder
	 * (os dois estão em {@link UsuariosConfig}).
	 */
	@Bean
	AuthenticationManager authenticationManager(UserDetailsService usuarios, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(usuarios);
		provider.setPasswordEncoder(passwordEncoder);
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
