package com.benepick.recommendation.dto;

import java.time.OffsetDateTime;

public record CatalogSyncStatusResponse(
    OffsetDateTime generatedAt,
    CatalogSyncTargetStatusResponse finlife,
    CatalogSyncTargetStatusResponse cards
) {
}
