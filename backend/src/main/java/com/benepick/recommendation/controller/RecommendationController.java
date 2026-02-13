package com.benepick.recommendation.controller;

import com.benepick.recommendation.dto.RecommendationAnalyticsResponse;
import com.benepick.recommendation.dto.RecommendationQualityReportResponse;
import com.benepick.recommendation.dto.RecommendationRedirectRequest;
import com.benepick.recommendation.dto.RecommendationRedirectResponse;
import com.benepick.recommendation.dto.RecommendationRunHistoryItemResponse;
import com.benepick.recommendation.dto.RecommendationRunResponse;
import com.benepick.recommendation.dto.SimulateRecommendationRequest;
import com.benepick.recommendation.service.RecommendationAnalyticsService;
import com.benepick.recommendation.service.RecommendationQualityLoopService;
import com.benepick.recommendation.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationAnalyticsService recommendationAnalyticsService;
    private final RecommendationQualityLoopService recommendationQualityLoopService;

    public RecommendationController(
        RecommendationService recommendationService,
        RecommendationAnalyticsService recommendationAnalyticsService,
        RecommendationQualityLoopService recommendationQualityLoopService
    ) {
        this.recommendationService = recommendationService;
        this.recommendationAnalyticsService = recommendationAnalyticsService;
        this.recommendationQualityLoopService = recommendationQualityLoopService;
    }

    @PostMapping("/simulate")
    public RecommendationRunResponse simulate(@Valid @RequestBody SimulateRecommendationRequest request) {
        return recommendationService.simulate(request);
    }

    @GetMapping("/history")
    public List<RecommendationRunHistoryItemResponse> getHistory(
        @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return recommendationService.getRecentRuns(limit);
    }

    @GetMapping("/{runId}")
    public RecommendationRunResponse getRun(@PathVariable UUID runId) {
        return recommendationService.getRun(runId);
    }

    @GetMapping("/{runId}/analytics")
    public RecommendationAnalyticsResponse getAnalytics(@PathVariable UUID runId) {
        return recommendationAnalyticsService.getAnalytics(runId);
    }

    @GetMapping("/quality/latest")
    public RecommendationQualityReportResponse getLatestQualityReport() {
        return recommendationQualityLoopService.getLatestReport();
    }

    @PostMapping("/quality/recompute")
    public RecommendationQualityReportResponse recomputeQualityReport() {
        return recommendationQualityLoopService.recomputeAndStore("manual-api");
    }

    @PostMapping("/{runId}/redirect")
    public RecommendationRedirectResponse redirect(
        @PathVariable UUID runId,
        @Valid @RequestBody RecommendationRedirectRequest request,
        HttpServletRequest servletRequest
    ) {
        return recommendationService.redirect(
            runId,
            request,
            servletRequest.getHeader("User-Agent"),
            extractClientIp(servletRequest),
            servletRequest.getHeader("Referer")
        );
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] ips = forwardedFor.split(",");
        return ips[0].trim();
    }
}
