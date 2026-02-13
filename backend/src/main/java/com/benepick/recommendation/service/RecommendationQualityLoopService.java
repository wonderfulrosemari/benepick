package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.RecommendationQualityCategoryMetricResponse;
import com.benepick.recommendation.dto.RecommendationQualityReportResponse;
import com.benepick.recommendation.entity.AccountCatalogEntity;
import com.benepick.recommendation.entity.CardCatalogEntity;
import com.benepick.recommendation.entity.RecommendationItemEntity;
import com.benepick.recommendation.entity.RecommendationQualityCategoryMetricEntity;
import com.benepick.recommendation.entity.RecommendationQualitySnapshotEntity;
import com.benepick.recommendation.entity.RecommendationRedirectEventEntity;
import com.benepick.recommendation.entity.RecommendationRunEntity;
import com.benepick.recommendation.repository.AccountCatalogRepository;
import com.benepick.recommendation.repository.CardCatalogRepository;
import com.benepick.recommendation.repository.RecommendationItemRepository;
import com.benepick.recommendation.repository.RecommendationQualitySnapshotRepository;
import com.benepick.recommendation.repository.RecommendationRedirectEventRepository;
import com.benepick.recommendation.repository.RecommendationRunRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationQualityLoopService {

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final RecommendationRedirectEventRepository recommendationRedirectEventRepository;
    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;
    private final RecommendationQualitySnapshotRepository recommendationQualitySnapshotRepository;
    private final RecommendationQualityLoopProperties properties;

    public RecommendationQualityLoopService(
        RecommendationRunRepository recommendationRunRepository,
        RecommendationItemRepository recommendationItemRepository,
        RecommendationRedirectEventRepository recommendationRedirectEventRepository,
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository,
        RecommendationQualitySnapshotRepository recommendationQualitySnapshotRepository,
        RecommendationQualityLoopProperties properties
    ) {
        this.recommendationRunRepository = recommendationRunRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.recommendationRedirectEventRepository = recommendationRedirectEventRepository;
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
        this.recommendationQualitySnapshotRepository = recommendationQualitySnapshotRepository;
        this.properties = properties;
    }

    @Transactional
    public RecommendationQualityReportResponse recomputeAndStore(String triggerSource) {
        OffsetDateTime windowEndAt = OffsetDateTime.now();
        OffsetDateTime windowStartAt = windowEndAt.minusDays(Math.max(1, properties.getWindowDays()));

        ComputationResult result = compute(windowStartAt, windowEndAt);

        RecommendationQualitySnapshotEntity snapshot = new RecommendationQualitySnapshotEntity(
            safe(triggerSource, "manual"),
            windowStartAt,
            windowEndAt,
            result.totalRuns(),
            result.totalRecommendationItems(),
            result.totalRedirects(),
            result.uniqueClickedProducts(),
            result.overallCtrPercent(),
            result.overallCvrPercent(),
            result.notes()
        );

        for (CategoryMetric metric : result.categoryMetrics()) {
            snapshot.addCategoryMetric(new RecommendationQualityCategoryMetricEntity(
                metric.categoryKey(),
                metric.categoryLabel(),
                metric.recommendedProducts(),
                metric.totalRedirects(),
                metric.uniqueClickedProducts(),
                metric.ctrPercent(),
                metric.cvrPercent(),
                metric.suggestedAction(),
                metric.suggestedWeightDeltaPercent(),
                metric.evidence()
            ));
        }

        RecommendationQualitySnapshotEntity saved = recommendationQualitySnapshotRepository.save(snapshot);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecommendationQualityReportResponse getLatestReport() {
        return recommendationQualitySnapshotRepository.findTopByOrderByGeneratedAtDesc()
            .map(this::toResponse)
            .orElseGet(this::emptyResponse);
    }

    private ComputationResult compute(OffsetDateTime windowStartAt, OffsetDateTime windowEndAt) {
        List<RecommendationRunEntity> runs = recommendationRunRepository
            .findByCreatedAtBetweenOrderByCreatedAtDesc(windowStartAt, windowEndAt);

        if (runs.isEmpty()) {
            return new ComputationResult(0, 0, 0, 0, 0, 0, List.of(), "분석 기간 내 추천 실행 이력이 없습니다.");
        }

        List<UUID> runIds = runs.stream().map(RecommendationRunEntity::getId).toList();

        List<RecommendationItemEntity> items = recommendationItemRepository.findByRecommendationRun_IdIn(runIds);
        List<RecommendationRedirectEventEntity> events = recommendationRedirectEventRepository.findByRecommendationRunIdIn(runIds);

        Map<String, Set<String>> accountTagsByProductId = new HashMap<>();
        for (AccountCatalogEntity account : accountCatalogRepository.findByActiveTrue()) {
            accountTagsByProductId.put(account.getProductKey(), normalizeSet(account.getTags()));
        }

        Map<String, Set<String>> cardTagsByProductId = new HashMap<>();
        Map<String, Set<String>> cardCategoriesByProductId = new HashMap<>();
        for (CardCatalogEntity card : cardCatalogRepository.findByActiveTrue()) {
            cardTagsByProductId.put(card.getProductKey(), normalizeSet(card.getTags()));
            cardCategoriesByProductId.put(card.getProductKey(), normalizeSet(card.getCategories()));
        }

        Map<String, CategoryAggregate> aggregateByCategory = new HashMap<>();
        Map<String, String> categoryByRunItemKey = new HashMap<>();
        Map<String, String> productByRunItemKey = new HashMap<>();

        for (RecommendationItemEntity item : items) {
            UUID runId = item.getRecommendationRun().getId();
            String runItemKey = buildRunItemKey(runId, item.getProductType(), item.getProductId());
            String productKey = buildProductKey(item.getProductType(), item.getProductId());

            String categoryKey = resolveCategoryKey(
                item,
                accountTagsByProductId,
                cardTagsByProductId,
                cardCategoriesByProductId
            );

            categoryByRunItemKey.put(runItemKey, categoryKey);
            productByRunItemKey.put(runItemKey, productKey);

            CategoryAggregate aggregate = aggregateByCategory.computeIfAbsent(
                categoryKey,
                this::newCategoryAggregate
            );
            aggregate.recommendedProducts++;
        }

        int totalRedirects = 0;
        Set<String> uniqueClickedProducts = new HashSet<>();

        for (RecommendationRedirectEventEntity event : events) {
            String runItemKey = buildRunItemKey(event.getRecommendationRunId(), event.getProductType(), event.getProductId());
            String categoryKey = categoryByRunItemKey.get(runItemKey);
            if (categoryKey == null) {
                continue;
            }

            CategoryAggregate aggregate = aggregateByCategory.computeIfAbsent(
                categoryKey,
                this::newCategoryAggregate
            );

            totalRedirects++;
            aggregate.totalRedirects++;

            String productKey = productByRunItemKey.getOrDefault(
                runItemKey,
                buildProductKey(event.getProductType(), event.getProductId())
            );
            aggregate.uniqueClickedProductKeys.add(productKey);
            uniqueClickedProducts.add(productKey);
        }

        int totalRecommendationItems = items.size();
        int overallCtrPercent = totalRecommendationItems == 0
            ? 0
            : (int) Math.round((totalRedirects * 100.0) / totalRecommendationItems);
        int overallCvrPercent = totalRecommendationItems == 0
            ? 0
            : (int) Math.round((uniqueClickedProducts.size() * 100.0) / totalRecommendationItems);

        List<CategoryMetric> metrics = aggregateByCategory.values().stream()
            .map(this::toCategoryMetric)
            .sorted((a, b) -> {
                int byRedirects = Integer.compare(b.totalRedirects(), a.totalRedirects());
                if (byRedirects != 0) {
                    return byRedirects;
                }
                return Integer.compare(b.recommendedProducts(), a.recommendedProducts());
            })
            .toList();

        String notes = "최근 " + properties.getWindowDays() + "일 추천 " + runs.size() + "건 기준 자동 집계";

        return new ComputationResult(
            runs.size(),
            totalRecommendationItems,
            totalRedirects,
            uniqueClickedProducts.size(),
            overallCtrPercent,
            overallCvrPercent,
            metrics,
            notes
        );
    }

    private String resolveCategoryKey(
        RecommendationItemEntity item,
        Map<String, Set<String>> accountTagsByProductId,
        Map<String, Set<String>> cardTagsByProductId,
        Map<String, Set<String>> cardCategoriesByProductId
    ) {
        String productType = normalize(item.getProductType());
        String productId = item.getProductId();

        if ("account".equals(productType)) {
            Set<String> tags = accountTagsByProductId.getOrDefault(productId, Set.of());
            return classifyAccountCategory(tags);
        }

        if ("card".equals(productType)) {
            Set<String> tags = cardTagsByProductId.getOrDefault(productId, Set.of());
            Set<String> categories = cardCategoriesByProductId.getOrDefault(productId, Set.of());
            return classifyCardCategory(tags, categories);
        }

        return "other";
    }

    private CategoryMetric toCategoryMetric(CategoryAggregate aggregate) {
        int ctrPercent = aggregate.recommendedProducts == 0
            ? 0
            : (int) Math.round((aggregate.totalRedirects * 100.0) / aggregate.recommendedProducts);

        int uniqueClicked = aggregate.uniqueClickedProductKeys.size();
        int cvrPercent = aggregate.recommendedProducts == 0
            ? 0
            : (int) Math.round((uniqueClicked * 100.0) / aggregate.recommendedProducts);

        TuningSuggestion suggestion = suggest(aggregate.recommendedProducts, ctrPercent, cvrPercent);

        String evidence = "추천 " + aggregate.recommendedProducts
            + "건, 클릭 " + aggregate.totalRedirects
            + "건(CTR " + ctrPercent + "%), 고유 클릭 " + uniqueClicked
            + "건(CVR " + cvrPercent + "%)";

        return new CategoryMetric(
            aggregate.categoryKey,
            aggregate.categoryLabel,
            aggregate.recommendedProducts,
            aggregate.totalRedirects,
            uniqueClicked,
            ctrPercent,
            cvrPercent,
            suggestion.action(),
            suggestion.weightDeltaPercent(),
            evidence
        );
    }

    private TuningSuggestion suggest(int recommendedProducts, int ctrPercent, int cvrPercent) {
        if (recommendedProducts < properties.getMinRecommendedProducts()) {
            return new TuningSuggestion("HOLD", 0);
        }

        if (ctrPercent >= properties.getHighCtrPercent() && cvrPercent >= properties.getHighCvrPercent()) {
            int gap = (ctrPercent - properties.getHighCtrPercent()) + (cvrPercent - properties.getHighCvrPercent());
            int delta = Math.min(
                properties.getMaxWeightAdjustmentPercent(),
                Math.max(5, gap / 2)
            );
            return new TuningSuggestion("UP", delta);
        }

        if (ctrPercent <= properties.getLowCtrPercent() || cvrPercent <= properties.getLowCvrPercent()) {
            int gap = Math.max(0, properties.getLowCtrPercent() - ctrPercent)
                + Math.max(0, properties.getLowCvrPercent() - cvrPercent);
            int delta = Math.min(
                properties.getMaxWeightAdjustmentPercent(),
                Math.max(5, gap / 2)
            );
            return new TuningSuggestion("DOWN", -delta);
        }

        return new TuningSuggestion("HOLD", 0);
    }

    private RecommendationQualityReportResponse toResponse(RecommendationQualitySnapshotEntity snapshot) {
        List<RecommendationQualityCategoryMetricResponse> categoryResponses = snapshot.getCategoryMetrics().stream()
            .map(metric -> new RecommendationQualityCategoryMetricResponse(
                metric.getCategoryKey(),
                metric.getCategoryLabel(),
                metric.getRecommendedProducts(),
                metric.getTotalRedirects(),
                metric.getUniqueClickedProducts(),
                metric.getCtrPercent(),
                metric.getCvrPercent(),
                metric.getSuggestedAction(),
                metric.getSuggestedWeightDeltaPercent(),
                metric.getEvidence()
            ))
            .toList();

        return new RecommendationQualityReportResponse(
            snapshot.getId(),
            snapshot.getTriggerSource(),
            snapshot.getGeneratedAt(),
            snapshot.getWindowStartAt(),
            snapshot.getWindowEndAt(),
            snapshot.getTotalRuns(),
            snapshot.getTotalRecommendationItems(),
            snapshot.getTotalRedirects(),
            snapshot.getUniqueClickedProducts(),
            snapshot.getOverallCtrPercent(),
            snapshot.getOverallCvrPercent(),
            snapshot.getNotes(),
            categoryResponses
        );
    }

    private RecommendationQualityReportResponse emptyResponse() {
        OffsetDateTime now = OffsetDateTime.now();
        return new RecommendationQualityReportResponse(
            null,
            "none",
            now,
            now,
            now,
            0,
            0,
            0,
            0,
            0,
            0,
            "아직 저장된 품질 집계가 없습니다.",
            List.of()
        );
    }

    private CategoryAggregate newCategoryAggregate(String categoryKey) {
        return new CategoryAggregate(categoryKey, labelForCategory(categoryKey));
    }

    private String labelForCategory(String categoryKey) {
        return switch (categoryKey) {
            case "savings" -> "저축/금리";
            case "salary" -> "급여/생활비";
            case "travel" -> "여행/해외";
            case "online" -> "온라인/구독";
            case "lifestyle" -> "생활소비";
            case "starter" -> "초보자/저비용";
            default -> "기타";
        };
    }

    private String classifyAccountCategory(Set<String> tags) {
        if (containsAny(tags, "savings", "goal", "auto")) {
            return "savings";
        }
        if (containsAny(tags, "travel", "global", "fx")) {
            return "travel";
        }
        if (containsAny(tags, "starter", "young", "low-fee")) {
            return "starter";
        }
        if (containsAny(tags, "salary", "daily", "cashback")) {
            return "salary";
        }
        return "other";
    }

    private String classifyCardCategory(Set<String> tags, Set<String> categories) {
        if (containsAny(tags, "travel", "mileage")) {
            return "travel";
        }
        if (containsAny(tags, "starter", "no-fee")) {
            return "starter";
        }
        if (containsAny(categories, "online", "subscription")) {
            return "online";
        }
        if (containsAny(categories, "grocery", "transport", "dining", "cafe") || containsAny(tags, "daily")) {
            return "lifestyle";
        }
        return "other";
    }

    private Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private boolean containsAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildRunItemKey(UUID runId, String productType, String productId) {
        return runId + "::" + normalize(productType) + "::" + normalize(productId);
    }

    private String buildProductKey(String productType, String productId) {
        return normalize(productType) + "::" + normalize(productId);
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static class CategoryAggregate {

        private final String categoryKey;
        private final String categoryLabel;
        private final Set<String> uniqueClickedProductKeys = new HashSet<>();
        private int recommendedProducts;
        private int totalRedirects;

        private CategoryAggregate(String categoryKey, String categoryLabel) {
            this.categoryKey = categoryKey;
            this.categoryLabel = categoryLabel;
        }
    }

    private record TuningSuggestion(String action, int weightDeltaPercent) {
    }

    private record CategoryMetric(
        String categoryKey,
        String categoryLabel,
        int recommendedProducts,
        int totalRedirects,
        int uniqueClickedProducts,
        int ctrPercent,
        int cvrPercent,
        String suggestedAction,
        int suggestedWeightDeltaPercent,
        String evidence
    ) {
    }

    private record ComputationResult(
        int totalRuns,
        int totalRecommendationItems,
        int totalRedirects,
        int uniqueClickedProducts,
        int overallCtrPercent,
        int overallCvrPercent,
        List<CategoryMetric> categoryMetrics,
        String notes
    ) {
    }
}
