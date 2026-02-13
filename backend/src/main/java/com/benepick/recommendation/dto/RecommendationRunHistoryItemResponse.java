package com.benepick.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecommendationRunHistoryItemResponse(
    UUID runId,
    String priority,
    int expectedNetMonthlyProfit,
    long redirectCount,
    OffsetDateTime createdAt
) {
}
