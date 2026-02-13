package com.benepick.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.google")
public record GoogleAuthProperties(
    String clientId,
    String jwkSetUri
) {
}
