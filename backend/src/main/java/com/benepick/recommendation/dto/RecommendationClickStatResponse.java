package com.benepick.recommendation.dto;

import java.time.OffsetDateTime;

public record RecommendationClickStatResponse(
    String productType,
    String productId,
    String provider,
    String name,
    int rank,
    int clickCount,
    OffsetDateTime lastClickedAt
) {
}
