package com.example.backend.auth;

import org.springframework.context.ApplicationListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.authentication.event.OidcUserRefreshedEvent;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * O {@code OidcAuthorizedClientRefreshedEventListener} troca o usuário (com o ID token novo) só na
 * requisição atual. Este listener grava essa troca na sessão, para valer nas próximas requisições.
 */
@Component
class SaveRefreshedOidcUserListener implements ApplicationListener<OidcUserRefreshedEvent> {

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	@Override
	public void onApplicationEvent(OidcUserRefreshedEvent event) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
				&& attributes.getResponse() != null) {
			securityContextRepository.saveContext(SecurityContextHolder.getContext(), attributes.getRequest(),
					attributes.getResponse());
		}
	}

}
