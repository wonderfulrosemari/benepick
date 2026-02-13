package com.benepick.auth.dto;

import java.util.UUID;

public record MeResponse(
    UUID userId,
    String email,
    String name,
    String profileImageUrl,
    String role
) {
}
