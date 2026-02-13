package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.CatalogSyncStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSyncStatusRepository extends JpaRepository<CatalogSyncStatusEntity, String> {
}
