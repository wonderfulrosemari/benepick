package com.benepick.recommendation.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationRunResponse(
    UUID runId,
    String priority,
    int expectedNetMonthlyProfit,
    List<RecommendationItemResponse> accounts,
    List<RecommendationItemResponse> cards,
    List<RecommendationBundleResponse> bundles
) {
}
