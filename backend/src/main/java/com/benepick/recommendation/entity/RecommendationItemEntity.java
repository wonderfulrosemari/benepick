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
@Table(name = "recommendation_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_run_id", nullable = false)
    private RecommendationRunEntity recommendationRun;

    @Column(nullable = false)
    private int rank;

    @Column(name = "product_type", nullable = false, length = 20)
    private String productType;

    @Column(name = "product_id", nullable = false, length = 80)
    private String productId;

    @Column(name = "provider_name", nullable = false, length = 80)
    private String providerName;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, length = 120)
    private String meta;

    @Column(nullable = false)
    private int score;

    @Column(name = "reason_text", nullable = false, length = 280)
    private String reasonText;

    @Column(name = "official_url", nullable = false, columnDefinition = "text")
    private String officialUrl;

    public RecommendationItemEntity(
        RecommendationRunEntity recommendationRun,
        int rank,
        String productType,
        String productId,
        String providerName,
        String productName,
        String summary,
        String meta,
        int score,
        String reasonText,
        String officialUrl
    ) {
        this.recommendationRun = recommendationRun;
        this.rank = rank;
        this.productType = productType;
        this.productId = productId;
        this.providerName = providerName;
        this.productName = productName;
        this.summary = summary;
        this.meta = meta;
        this.score = score;
        this.reasonText = reasonText;
        this.officialUrl = officialUrl;
    }
}
