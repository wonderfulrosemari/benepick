package com.benepick.recommendation.dto;

public record RecommendationItemResponse(
    int rank,
    String productType,
    String productId,
    String provider,
    String name,
    String summary,
    String meta,
    int score,
    String reason
) {
}
