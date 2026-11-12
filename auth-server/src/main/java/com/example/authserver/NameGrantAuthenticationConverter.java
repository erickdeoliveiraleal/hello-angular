package com.example.authserver;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lê o POST /oauth2/token. Se o grant_type for o de nome, transforma a requisição num
 * {@link NameGrantAuthenticationToken}; senão, devolve null e o Spring tenta os grants padrão.
 */
public class NameGrantAuthenticationConverter implements AuthenticationConverter {

	@Override
	public Authentication convert(HttpServletRequest request) {
		if (!NameGrant.GRANT_TYPE.getValue().equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
			return null;
		}

		String name = request.getParameter(NameGrant.NAME_PARAMETER);
		if (name == null || name.isBlank()) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Informe um nome", null));
		}

		// O cliente (backend) já foi autenticado antes, pelo client_id e segredo.
		Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

		Map<String, Object> additionalParameters = new HashMap<>();
		request.getParameterMap().forEach((key, values) -> {
			if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) && !key.equals(NameGrant.NAME_PARAMETER)) {
				additionalParameters.put(key, values[0]);
			}
		});

		return new NameGrantAuthenticationToken(name.trim(), clientPrincipal, additionalParameters);
	}

}
