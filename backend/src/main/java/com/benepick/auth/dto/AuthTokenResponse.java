package com.benepick.auth.dto;

public record AuthTokenResponse(
    String accessToken,
    long accessTokenExpiresInSeconds
) {
}
