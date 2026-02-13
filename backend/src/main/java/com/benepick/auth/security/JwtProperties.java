package com.benepick.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
    String secret,
    long accessTokenMinutes,
    long refreshTokenDays,
    String issuer
) {
}
