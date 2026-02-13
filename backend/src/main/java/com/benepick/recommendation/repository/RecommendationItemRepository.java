package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.RecommendationItemEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItemEntity, Long> {

    List<RecommendationItemEntity> findByRecommendationRun_IdOrderByProductTypeAscRankAsc(UUID recommendationRunId);

    Optional<RecommendationItemEntity> findByRecommendationRun_IdAndProductTypeAndProductId(
        UUID recommendationRunId,
        String productType,
        String productId
    );
}
