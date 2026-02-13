package com.benepick.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {

    private final RefreshCookieProperties properties;

    public RefreshTokenCookieManager(RefreshCookieProperties properties) {
        this.properties = properties;
    }

    public void writeRefreshToken(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
            .from(properties.name(), refreshToken)
            .httpOnly(true)
            .secure(properties.secure())
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite(properties.sameSite());

        if (properties.domain() != null && !properties.domain().isBlank()) {
            builder.domain(properties.domain());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
            .from(properties.name(), "")
            .httpOnly(true)
            .secure(properties.secure())
            .path("/")
            .maxAge(Duration.ZERO)
            .sameSite(properties.sameSite());

        if (properties.domain() != null && !properties.domain().isBlank()) {
            builder.domain(properties.domain());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
            .filter(cookie -> properties.name().equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .filter(value -> !value.isBlank());
    }
}
