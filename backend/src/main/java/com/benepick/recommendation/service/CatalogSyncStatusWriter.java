package com.benepick.recommendation.service;

import com.benepick.recommendation.entity.CatalogSyncStatusEntity;
import com.benepick.recommendation.repository.CatalogSyncStatusRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSyncStatusWriter {

    private final CatalogSyncStatusRepository catalogSyncStatusRepository;

    public CatalogSyncStatusWriter(CatalogSyncStatusRepository catalogSyncStatusRepository) {
        this.catalogSyncStatusRepository = catalogSyncStatusRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(
        String source,
        String trigger,
        String message,
        int fetched,
        int upserted,
        int deactivated,
        int skipped,
        OffsetDateTime runAt
    ) {
        CatalogSyncStatusEntity status = loadOrCreate(source);
        status.markSuccess(trigger, message, fetched, upserted, deactivated, skipped, runAt);
        catalogSyncStatusRepository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(String source, String trigger, String message, OffsetDateTime runAt) {
        CatalogSyncStatusEntity status = loadOrCreate(source);
        status.markFailure(trigger, message, runAt);
        catalogSyncStatusRepository.save(status);
    }

    private CatalogSyncStatusEntity loadOrCreate(String source) {
        return catalogSyncStatusRepository.findById(source)
            .orElseGet(() -> catalogSyncStatusRepository.save(new CatalogSyncStatusEntity(source)));
    }
}
