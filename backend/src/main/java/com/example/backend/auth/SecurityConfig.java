package com.example.backend.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizedClientRefreshedEventListener;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

	/**
	 * A API (/api/**): sem sessão e sem cookie. Só aceita "Authorization: Bearer <access token>",
	 * um JWT assinado pelo auth-server. Quem chama é o BFF, repassando o token do usuário.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/api/**")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/**
	 * O BFF: o que o navegador acessa. Login OAuth 2 com o auth-server (/oauth2/authorization/hello),
	 * sessão com cookie JSESSIONID e proteção CSRF. Os tokens ficam na sessão, no servidor.
	 */
	@Bean
	@Order(2)
	SecurityFilterChain bffSecurityFilterChain(HttpSecurity http,
			AuthServerLogoutSuccessHandler logoutSuccessHandler) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/bff/**").authenticated()
				.anyRequest().permitAll())
			// Depois do login no auth-server, volta para a página inicial do Angular.
			.oauth2Login(login -> login.defaultSuccessUrl("/", true))
			// Chamadas do Angular sem login recebem 401, em vez de serem redirecionadas para o login.
			.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					PathPatternRequestMatcher.withDefaults().matcher("/bff/**")))
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
			// POST /logout: encerra a sessão do BFF e devolve o endereço de logout do auth-server.
			.logout(logout -> logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler));
		return http.build();
	}

	/**
	 * Entrega o access token do usuário (guardado na sessão) e o renova com o refresh token quando
	 * está para expirar. O usuário e o Angular nem ficam sabendo.
	 */
	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrations,
			OAuth2AuthorizedClientRepository authorizedClients, ApplicationEventPublisher eventPublisher) {
		OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
			.authorizationCode()
			.refreshToken(refresh -> refresh
				// Renova quando faltam 5 segundos para expirar (o padrão é 60s, mais que o token de 1 minuto).
				.clockSkew(Duration.ofSeconds(5))
				// Avisa quando renovou: o OidcAuthorizedClientRefreshedEventListener troca o ID token do usuário.
				.eventPublisher(eventPublisher))
			.build();
		DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(clientRegistrations,
				authorizedClients);
		manager.setAuthorizedClientProvider(provider);
		return manager;
	}

	/**
	 * Na renovação, o auth-server também emite um ID token novo e descarta o antigo. Este listener
	 * (do Spring) atualiza o usuário logado com o ID token novo. Sem isso, o logout no auth-server
	 * falharia, porque o BFF mandaria um id_token_hint que o auth-server não reconhece mais.
	 */
	@Bean
	OidcAuthorizedClientRefreshedEventListener oidcAuthorizedClientRefreshedEventListener() {
		return new OidcAuthorizedClientRefreshedEventListener();
	}

	@Bean
	AuthServerLogoutSuccessHandler authServerLogoutSuccessHandler(
			@Value("${app.auth-server.logout-uri}") String logoutUri) {
		return new AuthServerLogoutSuccessHandler(logoutUri);
	}

}
