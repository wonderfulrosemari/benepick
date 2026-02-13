package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.RecommendationQualitySnapshotEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationQualitySnapshotRepository extends JpaRepository<RecommendationQualitySnapshotEntity, UUID> {

    Optional<RecommendationQualitySnapshotEntity> findTopByOrderByGeneratedAtDesc();
}
