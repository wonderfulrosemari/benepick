package com.benepick.recommendation.dto;

import java.time.OffsetDateTime;

public record CatalogSyncTargetStatusResponse(
    String source,
    String lastResult,
    String lastTrigger,
    OffsetDateTime lastRunAt,
    OffsetDateTime lastSuccessAt,
    OffsetDateTime lastFailureAt,
    String lastMessage,
    Integer lastFetched,
    Integer lastUpserted,
    Integer lastDeactivated,
    Integer lastSkipped,
    int consecutiveFailureCount
) {
}
