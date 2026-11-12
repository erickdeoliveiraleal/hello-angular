package com.example.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração dos JWTs do app móvel ({@code app.jwt.*} no application.properties). */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {
}
