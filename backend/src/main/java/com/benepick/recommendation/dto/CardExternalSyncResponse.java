package com.benepick.recommendation.dto;

public record CardExternalSyncResponse(
    int fetched,
    int upserted,
    int deactivated,
    int skipped
) {
}
