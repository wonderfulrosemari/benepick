package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.CardCatalogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardCatalogRepository extends JpaRepository<CardCatalogEntity, UUID> {

    List<CardCatalogEntity> findByActiveTrue();

    Optional<CardCatalogEntity> findByProductKey(String productKey);

    List<CardCatalogEntity> findByProductKeyStartingWith(String productKeyPrefix);

    long countByProductKeyStartingWith(String productKeyPrefix);
}
