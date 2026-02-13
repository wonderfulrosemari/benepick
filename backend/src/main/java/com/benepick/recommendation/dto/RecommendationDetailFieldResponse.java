package com.benepick.recommendation.dto;

public record RecommendationDetailFieldResponse(
    String label,
    String value,
    boolean link
) {
}
