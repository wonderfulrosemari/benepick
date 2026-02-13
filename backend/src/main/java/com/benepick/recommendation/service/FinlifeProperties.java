package com.benepick.recommendation.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "catalog.finlife")
public class FinlifeProperties {

    private String baseUrl = "https://finlife.fss.or.kr/finlifeapi";

    private String authKey = "";

    private List<String> topFinGrpNos = new ArrayList<>(List.of("020000"));

    private int maxPagesPerGroup = 2;

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 12000;
}
