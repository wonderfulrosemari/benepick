package com.benepick.auth.service;

public record AuthTokens(
    String accessToken,
    long accessTokenExpiresInSeconds,
    String refreshToken,
    long refreshTokenExpiresInSeconds
) {
}
