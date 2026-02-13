package com.benepick.recommendation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recommendation_quality_snapshot")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationQualitySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trigger_source", nullable = false, length = 30)
    private String triggerSource;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "window_start_at", nullable = false)
    private OffsetDateTime windowStartAt;

    @Column(name = "window_end_at", nullable = false)
    private OffsetDateTime windowEndAt;

    @Column(name = "total_runs", nullable = false)
    private int totalRuns;

    @Column(name = "total_recommendation_items", nullable = false)
    private int totalRecommendationItems;

    @Column(name = "total_redirects", nullable = false)
    private int totalRedirects;

    @Column(name = "unique_clicked_products", nullable = false)
    private int uniqueClickedProducts;

    @Column(name = "overall_ctr_percent", nullable = false)
    private int overallCtrPercent;

    @Column(name = "overall_cvr_percent", nullable = false)
    private int overallCvrPercent;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecommendationQualityCategoryMetricEntity> categoryMetrics = new ArrayList<>();

    public RecommendationQualitySnapshotEntity(
        String triggerSource,
        OffsetDateTime windowStartAt,
        OffsetDateTime windowEndAt,
        int totalRuns,
        int totalRecommendationItems,
        int totalRedirects,
        int uniqueClickedProducts,
        int overallCtrPercent,
        int overallCvrPercent,
        String notes
    ) {
        this.triggerSource = triggerSource;
        this.windowStartAt = windowStartAt;
        this.windowEndAt = windowEndAt;
        this.totalRuns = totalRuns;
        this.totalRecommendationItems = totalRecommendationItems;
        this.totalRedirects = totalRedirects;
        this.uniqueClickedProducts = uniqueClickedProducts;
        this.overallCtrPercent = overallCtrPercent;
        this.overallCvrPercent = overallCvrPercent;
        this.notes = notes;
    }

    @PrePersist
    void prePersist() {
        this.generatedAt = OffsetDateTime.now();
    }

    public void addCategoryMetric(RecommendationQualityCategoryMetricEntity metric) {
        metric.attachSnapshot(this);
        this.categoryMetrics.add(metric);
    }
}
