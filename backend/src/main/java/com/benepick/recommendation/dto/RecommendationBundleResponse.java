package com.benepick.recommendation.dto;

import java.util.List;

public record RecommendationBundleResponse(
    int rank,
    String title,
    String accountProductId,
    String accountLabel,
    String cardProductId,
    String cardLabel,
    int minExtraMonthlyBenefit,
    int expectedExtraMonthlyBenefit,
    int maxExtraMonthlyBenefit,
    int accountExpectedExtraMonthlyBenefit,
    int cardExpectedExtraMonthlyBenefit,
    int synergyExtraMonthlyBenefit,
    String estimateMethod,
    List<RecommendationBundleBenefitComponentResponse> benefitComponents,
    String reason
) {
}
