package com.benepick.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "catalog.card-external")
public class CardExternalProperties {

    private String sourceUrl = "";

    private int connectTimeoutMs = 4000;

    private int readTimeoutMs = 10000;
}
