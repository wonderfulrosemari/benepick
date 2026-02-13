package com.benepick.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "recommendation.quality")
public class RecommendationQualityLoopProperties {

    private boolean enabled = true;

    private boolean startupEnabled = true;

    private boolean scheduledEnabled = true;

    private String cron = "0 10 4 * * *";

    private String zone = "Asia/Seoul";

    private int windowDays = 14;

    private int minRecommendedProducts = 20;

    private int lowCtrPercent = 5;

    private int highCtrPercent = 18;

    private int lowCvrPercent = 3;

    private int highCvrPercent = 12;

    private int maxWeightAdjustmentPercent = 20;
}
