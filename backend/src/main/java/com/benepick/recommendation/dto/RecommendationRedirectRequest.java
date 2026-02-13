package com.benepick.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record RecommendationRedirectRequest(
    @NotBlank
    String productType,

    @NotBlank
    String productId
) {
}
