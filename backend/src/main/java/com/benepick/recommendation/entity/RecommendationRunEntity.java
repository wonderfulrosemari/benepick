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
@Table(name = "recommendation_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "expected_net_monthly_profit", nullable = false)
    private int expectedNetMonthlyProfit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RecommendationRunEntity(String priority, int expectedNetMonthlyProfit) {
        this.priority = priority;
        this.expectedNetMonthlyProfit = expectedNetMonthlyProfit;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
