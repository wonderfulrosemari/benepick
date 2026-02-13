package com.benepick.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "catalog.sync")
public class CatalogSyncSchedulerProperties {

    /**
     * 전체 자동 동기화 기능 on/off
     */
    private boolean enabled = true;

    /**
     * 앱 시작 후 1회 동기화 실행 여부
     */
    private boolean startupEnabled = true;

    /**
     * 정기 스케줄 동기화 실행 여부
     */
    private boolean scheduledEnabled = true;

    /**
     * 계좌(Finlife) 동기화 실행 여부
     */
    private boolean finlifeEnabled = true;

    /**
     * 카드(External/Public Data) 동기화 실행 여부
     */
    private boolean cardsEnabled = true;

    /**
     * 6-field spring cron (second minute hour day month weekday)
     */
    private String cron = "0 30 3 * * *";

    /**
     * Cron timezone
     */
    private String zone = "Asia/Seoul";
}
