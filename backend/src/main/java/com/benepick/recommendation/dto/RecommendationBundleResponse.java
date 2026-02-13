package com.benepick.recommendation.dto;

public record RecommendationBundleResponse(
    int rank,
    String title,
    String accountProductId,
    String accountLabel,
    String cardProductId,
    String cardLabel,
    int expectedExtraMonthlyBenefit,
    String reason
) {
}
