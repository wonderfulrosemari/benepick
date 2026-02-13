package com.benepick.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.cookie")
public record RefreshCookieProperties(
    String name,
    boolean secure,
    String sameSite,
    String domain
) {
}
