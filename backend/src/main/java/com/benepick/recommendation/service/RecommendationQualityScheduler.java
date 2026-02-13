package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.RecommendationQualityReportResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecommendationQualityScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecommendationQualityScheduler.class);

    private final RecommendationQualityLoopService recommendationQualityLoopService;
    private final RecommendationQualityLoopProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RecommendationQualityScheduler(
        RecommendationQualityLoopService recommendationQualityLoopService,
        RecommendationQualityLoopProperties properties
    ) {
        this.recommendationQualityLoopService = recommendationQualityLoopService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        if (!properties.isEnabled() || !properties.isStartupEnabled()) {
            log.info(
                "Recommendation quality startup loop skipped (enabled={}, startupEnabled={})",
                properties.isEnabled(),
                properties.isStartupEnabled()
            );
            return;
        }
        runLoop("startup");
    }

    @Scheduled(cron = "#{@recommendationQualityLoopProperties.cron}", zone = "#{@recommendationQualityLoopProperties.zone}")
    public void runBySchedule() {
        if (!properties.isEnabled() || !properties.isScheduledEnabled()) {
            return;
        }
        runLoop("scheduled");
    }

    private void runLoop(String triggerSource) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Recommendation quality loop skipped because previous loop is still running (trigger={})", triggerSource);
            return;
        }

        try {
            RecommendationQualityReportResponse report = recommendationQualityLoopService.recomputeAndStore(triggerSource);
            log.info(
                "Recommendation quality loop completed (trigger={}, runs={}, items={}, redirects={}, categories={})",
                triggerSource,
                report.totalRuns(),
                report.totalRecommendationItems(),
                report.totalRedirects(),
                report.categories().size()
            );
        } catch (Exception exception) {
            log.warn("Recommendation quality loop failed (trigger={}): {}", triggerSource, exception.getMessage());
        } finally {
            running.set(false);
        }
    }
}
