package com.benepick.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recommendation_redirect_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRedirectEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_run_id", nullable = false)
    private UUID recommendationRunId;

    @Column(name = "product_type", nullable = false, length = 20)
    private String productType;

    @Column(name = "product_id", nullable = false, length = 80)
    private String productId;

    @Column(name = "official_url", nullable = false, columnDefinition = "text")
    private String officialUrl;

    @Column(name = "clicked_at", nullable = false)
    private OffsetDateTime clickedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String referrer;

    public RecommendationRedirectEventEntity(
        UUID recommendationRunId,
        String productType,
        String productId,
        String officialUrl,
        String userAgent,
        String ipAddress,
        String referrer
    ) {
        this.recommendationRunId = recommendationRunId;
        this.productType = productType;
        this.productId = productId;
        this.officialUrl = officialUrl;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.referrer = referrer;
    }

    @PrePersist
    void prePersist() {
        this.clickedAt = OffsetDateTime.now();
    }
}
