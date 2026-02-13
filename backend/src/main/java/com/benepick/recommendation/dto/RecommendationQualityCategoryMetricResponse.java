package com.benepick.recommendation.dto;

public record RecommendationQualityCategoryMetricResponse(
    String categoryKey,
    String categoryLabel,
    int recommendedProducts,
    int totalRedirects,
    int uniqueClickedProducts,
    int ctrPercent,
    int cvrPercent,
    String suggestedAction,
    int suggestedWeightDeltaPercent,
    String evidence
) {
}
