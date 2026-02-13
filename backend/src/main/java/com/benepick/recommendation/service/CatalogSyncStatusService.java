package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.CardExternalSyncResponse;
import com.benepick.recommendation.dto.CatalogSyncStatusResponse;
import com.benepick.recommendation.dto.CatalogSyncTargetStatusResponse;
import com.benepick.recommendation.dto.FinlifeSyncResponse;
import com.benepick.recommendation.entity.CatalogSyncStatusEntity;
import com.benepick.recommendation.repository.CatalogSyncStatusRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSyncStatusService {

    public static final String SOURCE_FINLIFE = "FINLIFE";
    public static final String SOURCE_CARDS = "CARDS";

    private final CatalogSyncService catalogSyncService;
    private final CatalogSyncStatusRepository catalogSyncStatusRepository;
    private final CatalogSyncStatusWriter catalogSyncStatusWriter;

    public CatalogSyncStatusService(
        CatalogSyncService catalogSyncService,
        CatalogSyncStatusRepository catalogSyncStatusRepository,
        CatalogSyncStatusWriter catalogSyncStatusWriter
    ) {
        this.catalogSyncService = catalogSyncService;
        this.catalogSyncStatusRepository = catalogSyncStatusRepository;
        this.catalogSyncStatusWriter = catalogSyncStatusWriter;
    }

    @Transactional(readOnly = true)
    public CatalogSyncStatusResponse getSyncStatus() {
        return new CatalogSyncStatusResponse(
            OffsetDateTime.now(),
            readStatusOrDefault(SOURCE_FINLIFE),
            readStatusOrDefault(SOURCE_CARDS)
        );
    }

    public FinlifeSyncResponse syncFinlifeWithStatus(String trigger) {
        OffsetDateTime runAt = OffsetDateTime.now();

        try {
            FinlifeSyncResponse response = catalogSyncService.syncAccountsFromFinlife();
            catalogSyncStatusWriter.markSuccess(
                SOURCE_FINLIFE,
                trigger,
                "Finlife sync completed",
                response.fetchedProducts(),
                response.upsertedProducts(),
                response.deactivatedProducts(),
                response.skippedProducts(),
                runAt
            );
            return response;
        } catch (Exception exception) {
            catalogSyncStatusWriter.markFailure(SOURCE_FINLIFE, trigger, rootMessage(exception), runAt);
            throw exception;
        }
    }

    public CardExternalSyncResponse syncCardsWithStatus(String trigger) {
        OffsetDateTime runAt = OffsetDateTime.now();

        try {
            CardExternalSyncResponse response = catalogSyncService.syncCardsFromExternal();
            catalogSyncStatusWriter.markSuccess(
                SOURCE_CARDS,
                trigger,
                "Card external sync completed",
                response.fetched(),
                response.upserted(),
                response.deactivated(),
                response.skipped(),
                runAt
            );
            return response;
        } catch (Exception exception) {
            catalogSyncStatusWriter.markFailure(SOURCE_CARDS, trigger, rootMessage(exception), runAt);
            throw exception;
        }
    }

    private CatalogSyncTargetStatusResponse readStatusOrDefault(String source) {
        return catalogSyncStatusRepository.findById(source)
            .map(this::toStatusResponse)
            .orElseGet(() -> defaultStatus(source));
    }

    private CatalogSyncTargetStatusResponse defaultStatus(String source) {
        return new CatalogSyncTargetStatusResponse(
            source,
            "NEVER",
            "",
            null,
            null,
            null,
            "아직 동기화 실행 이력이 없습니다.",
            null,
            null,
            null,
            null,
            0
        );
    }

    private CatalogSyncTargetStatusResponse toStatusResponse(CatalogSyncStatusEntity status) {
        return new CatalogSyncTargetStatusResponse(
            status.getSyncSource(),
            status.getLastResult(),
            status.getLastTrigger(),
            status.getLastRunAt(),
            status.getLastSuccessAt(),
            status.getLastFailureAt(),
            status.getLastMessage(),
            status.getLastFetched(),
            status.getLastUpserted(),
            status.getLastDeactivated(),
            status.getLastSkipped(),
            status.getConsecutiveFailureCount()
        );
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }

        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
