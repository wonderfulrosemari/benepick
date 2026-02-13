package com.benepick.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "catalog_sync_status")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogSyncStatusEntity {

    @Id
    @Column(name = "sync_source", nullable = false, length = 30)
    private String syncSource;

    @Column(name = "last_result", nullable = false, length = 20)
    private String lastResult;

    @Column(name = "last_trigger", length = 40)
    private String lastTrigger;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private OffsetDateTime lastFailureAt;

    @Column(name = "last_message", columnDefinition = "text")
    private String lastMessage;

    @Column(name = "last_fetched")
    private Integer lastFetched;

    @Column(name = "last_upserted")
    private Integer lastUpserted;

    @Column(name = "last_deactivated")
    private Integer lastDeactivated;

    @Column(name = "last_skipped")
    private Integer lastSkipped;

    @Column(name = "consecutive_failure_count", nullable = false)
    private int consecutiveFailureCount;

    public CatalogSyncStatusEntity(String syncSource) {
        this.syncSource = syncSource;
        this.lastResult = "NEVER";
        this.consecutiveFailureCount = 0;
    }

    public void markSuccess(
        String trigger,
        String message,
        int fetched,
        int upserted,
        int deactivated,
        int skipped,
        OffsetDateTime runAt
    ) {
        this.lastResult = "SUCCESS";
        this.lastTrigger = safe(trigger);
        this.lastRunAt = runAt;
        this.lastSuccessAt = runAt;
        this.lastMessage = trim(message);
        this.lastFetched = fetched;
        this.lastUpserted = upserted;
        this.lastDeactivated = deactivated;
        this.lastSkipped = skipped;
        this.consecutiveFailureCount = 0;
    }

    public void markFailure(String trigger, String message, OffsetDateTime runAt) {
        this.lastResult = "FAILED";
        this.lastTrigger = safe(trigger);
        this.lastRunAt = runAt;
        this.lastFailureAt = runAt;
        this.lastMessage = trim(message);
        this.consecutiveFailureCount += 1;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trim(String message) {
        if (message == null) {
            return "";
        }

        String normalized = message.trim();
        if (normalized.length() <= 1000) {
            return normalized;
        }
        return normalized.substring(0, 1000);
    }
}
