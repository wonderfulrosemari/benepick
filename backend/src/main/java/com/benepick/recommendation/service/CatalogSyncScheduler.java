package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.CardExternalSyncResponse;
import com.benepick.recommendation.dto.FinlifeSyncResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncScheduler.class);

    private final CatalogSyncStatusService catalogSyncStatusService;
    private final CatalogSyncSchedulerProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CatalogSyncScheduler(CatalogSyncStatusService catalogSyncStatusService, CatalogSyncSchedulerProperties properties) {
        this.catalogSyncStatusService = catalogSyncStatusService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        if (!properties.isEnabled() || !properties.isStartupEnabled()) {
            log.info("Catalog startup sync skipped (enabled={}, startupEnabled={})", properties.isEnabled(), properties.isStartupEnabled());
            return;
        }
        runSync("startup");
    }

    @Scheduled(cron = "#{@catalogSyncSchedulerProperties.cron}", zone = "#{@catalogSyncSchedulerProperties.zone}")
    public void runBySchedule() {
        if (!properties.isEnabled() || !properties.isScheduledEnabled()) {
            return;
        }
        runSync("scheduled");
    }

    private void runSync(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Catalog sync skipped because previous sync is still running (trigger={})", trigger);
            return;
        }

        try {
            if (properties.isFinlifeEnabled()) {
                try {
                    FinlifeSyncResponse finlife = catalogSyncStatusService.syncFinlifeWithStatus(trigger);
                    log.info(
                        "Catalog finlife sync completed (trigger={}, fetched={}, upserted={}, deactivated={}, skipped={})",
                        trigger,
                        finlife.fetchedProducts(),
                        finlife.upsertedProducts(),
                        finlife.deactivatedProducts(),
                        finlife.skippedProducts()
                    );
                } catch (Exception exception) {
                    log.warn("Catalog finlife sync failed (trigger={}): {}", trigger, exception.getMessage());
                }
            }

            if (properties.isCardsEnabled()) {
                try {
                    CardExternalSyncResponse cards = catalogSyncStatusService.syncCardsWithStatus(trigger);
                    log.info(
                        "Catalog card sync completed (trigger={}, fetched={}, upserted={}, deactivated={}, skipped={})",
                        trigger,
                        cards.fetched(),
                        cards.upserted(),
                        cards.deactivated(),
                        cards.skipped()
                    );
                } catch (Exception exception) {
                    log.warn("Catalog card sync failed (trigger={}): {}", trigger, exception.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }
}
