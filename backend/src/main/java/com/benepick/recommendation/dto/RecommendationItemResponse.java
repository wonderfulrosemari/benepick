package com.benepick.recommendation.dto;

import java.util.List;

public record RecommendationItemResponse(
    int rank,
    String productType,
    String productId,
    String provider,
    String name,
    String summary,
    String meta,
    int score,
    String reason,
    int minExpectedMonthlyBenefit,
    int expectedMonthlyBenefit,
    int maxExpectedMonthlyBenefit,
    String estimateMethod,
    List<RecommendationBundleBenefitComponentResponse> benefitComponents,
    List<RecommendationDetailFieldResponse> detailFields
) {
}
