package com.benepick.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecommendationQualityReportResponse(
    UUID snapshotId,
    String triggerSource,
    OffsetDateTime generatedAt,
    OffsetDateTime windowStartAt,
    OffsetDateTime windowEndAt,
    int totalRuns,
    int totalRecommendationItems,
    int totalRedirects,
    int uniqueClickedProducts,
    int overallCtrPercent,
    int overallCvrPercent,
    String notes,
    List<RecommendationQualityCategoryMetricResponse> categories
) {
}
