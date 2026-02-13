package com.benepick.recommendation.dto;

public record RecommendationBundleBenefitComponentResponse(
    String key,
    String label,
    String condition,
    int amountWonPerMonth,
    boolean applied
) {
}
