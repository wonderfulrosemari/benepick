package com.benepick.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recommendation_quality_category_metric")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationQualityCategoryMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private RecommendationQualitySnapshotEntity snapshot;

    @Column(name = "category_key", nullable = false, length = 30)
    private String categoryKey;

    @Column(name = "category_label", nullable = false, length = 60)
    private String categoryLabel;

    @Column(name = "recommended_products", nullable = false)
    private int recommendedProducts;

    @Column(name = "total_redirects", nullable = false)
    private int totalRedirects;

    @Column(name = "unique_clicked_products", nullable = false)
    private int uniqueClickedProducts;

    @Column(name = "ctr_percent", nullable = false)
    private int ctrPercent;

    @Column(name = "cvr_percent", nullable = false)
    private int cvrPercent;

    @Column(name = "suggested_action", nullable = false, length = 20)
    private String suggestedAction;

    @Column(name = "suggested_weight_delta_percent", nullable = false)
    private int suggestedWeightDeltaPercent;

    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    public RecommendationQualityCategoryMetricEntity(
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
        this.categoryKey = categoryKey;
        this.categoryLabel = categoryLabel;
        this.recommendedProducts = recommendedProducts;
        this.totalRedirects = totalRedirects;
        this.uniqueClickedProducts = uniqueClickedProducts;
        this.ctrPercent = ctrPercent;
        this.cvrPercent = cvrPercent;
        this.suggestedAction = suggestedAction;
        this.suggestedWeightDeltaPercent = suggestedWeightDeltaPercent;
        this.evidence = evidence;
    }

    void attachSnapshot(RecommendationQualitySnapshotEntity snapshot) {
        this.snapshot = snapshot;
    }
}
