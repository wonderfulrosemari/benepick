package com.benepick.recommendation.dto;

public record FinlifeSyncResponse(
    int fetchedProducts,
    int upsertedProducts,
    int deactivatedProducts,
    int skippedProducts
) {
}
