package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.RecommendationRedirectEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRedirectEventRepository extends JpaRepository<RecommendationRedirectEventEntity, UUID> {

    List<RecommendationRedirectEventEntity> findByRecommendationRunId(UUID recommendationRunId);

    long countByRecommendationRunId(UUID recommendationRunId);
}
