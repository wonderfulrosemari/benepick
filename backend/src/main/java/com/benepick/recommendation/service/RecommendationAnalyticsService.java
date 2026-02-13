package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.RecommendationAnalyticsResponse;
import com.benepick.recommendation.dto.RecommendationCategoryStatResponse;
import com.benepick.recommendation.dto.RecommendationClickStatResponse;
import com.benepick.recommendation.entity.AccountCatalogEntity;
import com.benepick.recommendation.entity.CardCatalogEntity;
import com.benepick.recommendation.entity.RecommendationItemEntity;
import com.benepick.recommendation.entity.RecommendationRedirectEventEntity;
import com.benepick.recommendation.repository.AccountCatalogRepository;
import com.benepick.recommendation.repository.CardCatalogRepository;
import com.benepick.recommendation.repository.RecommendationItemRepository;
import com.benepick.recommendation.repository.RecommendationRedirectEventRepository;
import com.benepick.recommendation.repository.RecommendationRunRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationAnalyticsService {

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final RecommendationRedirectEventRepository recommendationRedirectEventRepository;
    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;

    public RecommendationAnalyticsService(
        RecommendationRunRepository recommendationRunRepository,
        RecommendationItemRepository recommendationItemRepository,
        RecommendationRedirectEventRepository recommendationRedirectEventRepository,
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository
    ) {
        this.recommendationRunRepository = recommendationRunRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.recommendationRedirectEventRepository = recommendationRedirectEventRepository;
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
    }

    @Transactional(readOnly = true)
    public RecommendationAnalyticsResponse getAnalytics(UUID runId) {
        recommendationRunRepository.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation run not found"));

        List<RecommendationItemEntity> items = recommendationItemRepository
            .findByRecommendationRun_IdOrderByProductTypeAscRankAsc(runId);

        List<RecommendationRedirectEventEntity> events = recommendationRedirectEventRepository
            .findByRecommendationRunId(runId);

        Map<String, RecommendationItemEntity> itemByKey = new HashMap<>();
        for (RecommendationItemEntity item : items) {
            itemByKey.put(buildKey(item.getProductType(), item.getProductId()), item);
        }

        Map<String, String> categoryByItemKey = resolveCategoryByItemKey(items);

        Map<String, Integer> clickCountByKey = new HashMap<>();
        Map<String, OffsetDateTime> lastClickedAtByKey = new HashMap<>();

        Map<String, CategoryAggregate> categoryAggregateByKey = new HashMap<>();
        for (RecommendationItemEntity item : items) {
            String itemKey = buildKey(item.getProductType(), item.getProductId());
            String categoryKey = categoryByItemKey.getOrDefault(itemKey, "other");
            CategoryAggregate aggregate = categoryAggregateByKey.computeIfAbsent(
                categoryKey,
                this::newCategoryAggregate
            );
            aggregate.recommendedProducts++;
        }

        for (RecommendationRedirectEventEntity event : events) {
            String key = buildKey(event.getProductType(), event.getProductId());
            clickCountByKey.put(key, clickCountByKey.getOrDefault(key, 0) + 1);

            OffsetDateTime clickedAt = event.getClickedAt();
            OffsetDateTime previous = lastClickedAtByKey.get(key);
            if (previous == null || (clickedAt != null && clickedAt.isAfter(previous))) {
                lastClickedAtByKey.put(key, clickedAt);
            }

            String categoryKey = categoryByItemKey.get(key);
            if (categoryKey != null) {
                CategoryAggregate aggregate = categoryAggregateByKey.computeIfAbsent(
                    categoryKey,
                    this::newCategoryAggregate
                );
                aggregate.totalRedirects++;
                aggregate.uniqueClickedItemKeys.add(key);
            }
        }

        List<RecommendationClickStatResponse> topClicked = clickCountByKey.entrySet().stream()
            .filter(entry -> itemByKey.containsKey(entry.getKey()))
            .map(entry -> {
                RecommendationItemEntity item = itemByKey.get(entry.getKey());
                return new RecommendationClickStatResponse(
                    item.getProductType(),
                    item.getProductId(),
                    item.getProviderName(),
                    item.getProductName(),
                    item.getRank(),
                    entry.getValue(),
                    lastClickedAtByKey.get(entry.getKey())
                );
            })
            .sorted((a, b) -> {
                int byCount = Integer.compare(b.clickCount(), a.clickCount());
                if (byCount != 0) {
                    return byCount;
                }

                OffsetDateTime aClickedAt = a.lastClickedAt();
                OffsetDateTime bClickedAt = b.lastClickedAt();
                if (aClickedAt == null && bClickedAt == null) {
                    return 0;
                }
                if (aClickedAt == null) {
                    return 1;
                }
                if (bClickedAt == null) {
                    return -1;
                }
                return bClickedAt.compareTo(aClickedAt);
            })
            .limit(5)
            .toList();

        List<RecommendationCategoryStatResponse> categoryStats = new ArrayList<>(
            categoryAggregateByKey.values().stream()
                .map(aggregate -> {
                    int recommended = aggregate.recommendedProducts;
                    int totalRedirects = aggregate.totalRedirects;
                    int uniqueClicked = aggregate.uniqueClickedItemKeys.size();

                    int clickRatePercent = recommended == 0
                        ? 0
                        : (int) Math.round((totalRedirects * 100.0) / recommended);

                    int conversionRatePercent = recommended == 0
                        ? 0
                        : (int) Math.round((uniqueClicked * 100.0) / recommended);

                    return new RecommendationCategoryStatResponse(
                        aggregate.categoryKey,
                        aggregate.categoryLabel,
                        recommended,
                        totalRedirects,
                        uniqueClicked,
                        clickRatePercent,
                        conversionRatePercent
                    );
                })
                .sorted((a, b) -> {
                    int byRedirects = Integer.compare(b.totalRedirects(), a.totalRedirects());
                    if (byRedirects != 0) {
                        return byRedirects;
                    }
                    return Integer.compare(b.recommendedProducts(), a.recommendedProducts());
                })
                .toList()
        );

        long uniqueClicked = clickCountByKey.keySet().stream().filter(itemByKey::containsKey).count();
        int totalItems = items.size();
        int uniqueClickRatePercent = totalItems == 0
            ? 0
            : (int) Math.round((uniqueClicked * 100.0) / totalItems);

        return new RecommendationAnalyticsResponse(
            runId,
            totalItems,
            events.size(),
            (int) uniqueClicked,
            uniqueClickRatePercent,
            topClicked,
            categoryStats
        );
    }

    private Map<String, String> resolveCategoryByItemKey(List<RecommendationItemEntity> items) {
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

        Map<String, String> categoryByItemKey = new HashMap<>();
        for (RecommendationItemEntity item : items) {
            String itemKey = buildKey(item.getProductType(), item.getProductId());
            String productType = normalize(item.getProductType());

            if ("account".equals(productType)) {
                Set<String> tags = accountTagsByProductId.getOrDefault(item.getProductId(), Set.of());
                categoryByItemKey.put(itemKey, classifyAccountCategory(tags));
                continue;
            }

            if ("card".equals(productType)) {
                Set<String> tags = cardTagsByProductId.getOrDefault(item.getProductId(), Set.of());
                Set<String> categories = cardCategoriesByProductId.getOrDefault(item.getProductId(), Set.of());
                categoryByItemKey.put(itemKey, classifyCardCategory(tags, categories));
                continue;
            }

            categoryByItemKey.put(itemKey, "other");
        }

        return categoryByItemKey;
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

    private Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(normalize(value));
            }
        }
        return normalized;
    }

    private boolean containsAny(Set<String> source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String buildKey(String productType, String productId) {
        return productType + "::" + productId;
    }

    private static final class CategoryAggregate {

        private final String categoryKey;
        private final String categoryLabel;
        private int recommendedProducts;
        private int totalRedirects;
        private final Set<String> uniqueClickedItemKeys = new HashSet<>();

        private CategoryAggregate(String categoryKey, String categoryLabel) {
            this.categoryKey = categoryKey;
            this.categoryLabel = categoryLabel;
        }
    }
}
