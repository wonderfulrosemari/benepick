package com.benepick.recommendation.repository;

import com.benepick.recommendation.entity.AccountCatalogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountCatalogRepository extends JpaRepository<AccountCatalogEntity, UUID> {

    List<AccountCatalogEntity> findByActiveTrue();

    Optional<AccountCatalogEntity> findByProductKey(String productKey);

    List<AccountCatalogEntity> findByProductKeyStartingWith(String prefix);

    long countByProductKeyStartingWith(String prefix);
}
