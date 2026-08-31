package com.ditec.assistencia.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ditec.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
