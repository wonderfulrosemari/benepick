package com.benepick.auth.service;

import java.util.UUID;

public record CurrentUserInfo(
    UUID userId,
    String email,
    String name,
    String profileImageUrl,
    String role
) {
}
