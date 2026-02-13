package com.benepick.auth.service;

public record GoogleUserInfo(
    String providerUserId,
    String email,
    String name,
    String profileImageUrl
) {
}
