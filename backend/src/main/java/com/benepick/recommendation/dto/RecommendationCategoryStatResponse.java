package com.benepick.recommendation.dto;

public record RecommendationCategoryStatResponse(
    String categoryKey,
    String categoryLabel,
    int recommendedProducts,
    int totalRedirects,
    int uniqueClickedProducts,
    int clickRatePercent,
    int conversionRatePercent
) {
}
