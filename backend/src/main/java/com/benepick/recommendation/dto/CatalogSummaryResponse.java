package com.benepick.recommendation.dto;

public record CatalogSummaryResponse(
    long totalAccounts,
    long finlifeAccounts,
    long totalCards,
    long externalCards
) {
}
