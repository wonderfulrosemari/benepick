package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.RecommendationRunEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRunRepository extends JpaRepository<RecommendationRunEntity, UUID> {

    List<RecommendationRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
