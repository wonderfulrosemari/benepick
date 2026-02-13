package com.benepick.recommendation.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationAnalyticsResponse(
    UUID runId,
    int totalRecommendationItems,
    int totalRedirects,
    int uniqueClickedProducts,
    int uniqueClickRatePercent,
    List<RecommendationClickStatResponse> topClickedProducts,
    List<RecommendationCategoryStatResponse> categoryStats
) {
}
