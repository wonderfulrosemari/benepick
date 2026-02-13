package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.CardExternalSyncResponse;
import com.benepick.recommendation.dto.CatalogSummaryResponse;
import com.benepick.recommendation.dto.FinlifeSyncResponse;
import com.benepick.recommendation.entity.AccountCatalogEntity;
import com.benepick.recommendation.entity.CardCatalogEntity;
import com.benepick.recommendation.repository.AccountCatalogRepository;
import com.benepick.recommendation.repository.CardCatalogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogSyncService {

    private static final String FINLIFE_KEY_PREFIX = "finlife:";
    private static final String FINLIFE_FALLBACK_URL = "https://finlife.fss.or.kr";
    private static final String CARD_EXTERNAL_KEY_PREFIX = "external:";
    private static final String CARD_EXTERNAL_FALLBACK_URL = "https://www.card-gorilla.com";

    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;
    private final FinlifeApiClient finlifeApiClient;
    private final FinlifeProperties finlifeProperties;
    private final CardExternalApiClient cardExternalApiClient;
    private final ProductUrlOverrideService productUrlOverrideService;

    public CatalogSyncService(
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository,
        FinlifeApiClient finlifeApiClient,
        FinlifeProperties finlifeProperties,
        CardExternalApiClient cardExternalApiClient,
        ProductUrlOverrideService productUrlOverrideService
    ) {
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
        this.finlifeApiClient = finlifeApiClient;
        this.finlifeProperties = finlifeProperties;
        this.cardExternalApiClient = cardExternalApiClient;
        this.productUrlOverrideService = productUrlOverrideService;
    }

    @Transactional(readOnly = true)
    public CatalogSummaryResponse getCatalogSummary() {
        return new CatalogSummaryResponse(
            accountCatalogRepository.count(),
            accountCatalogRepository.countByProductKeyStartingWith(FINLIFE_KEY_PREFIX),
            cardCatalogRepository.count(),
            cardCatalogRepository.countByProductKeyStartingWith(CARD_EXTERNAL_KEY_PREFIX)
        );
    }

    @Transactional
    public FinlifeSyncResponse syncAccountsFromFinlife() {
        if (safe(finlifeProperties.getAuthKey()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FINLIFE_AUTH_KEY is not configured");
        }

        List<String> topGroups = sanitizeTopGroups(finlifeProperties.getTopFinGrpNos());
        if (topGroups.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalog.finlife.top-fin-grp-nos is empty");
        }

        Map<String, String> companyUrls = fetchCompanyHomeUrls(topGroups);
        List<FinlifeProduct> fetchedProducts = fetchProducts(topGroups);
        if (fetchedProducts.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Finlife sync returned no products. Check FINLIFE_AUTH_KEY and top group code."
            );
        }

        int upserted = 0;
        int skipped = 0;
        Set<String> activeFinlifeKeys = new HashSet<>();
        Map<String, String> officialUrlOverrides = productUrlOverrideService.loadOverrides();

        for (FinlifeProduct product : fetchedProducts) {
            if (product.productCode().isBlank() || product.providerName().isBlank() || product.productName().isBlank()) {
                skipped++;
                continue;
            }

            String productKey = FINLIFE_KEY_PREFIX
                + product.kindCode()
                + ":"
                + sanitizeIdPart(product.finCoNo())
                + ":"
                + sanitizeIdPart(product.productCode());
            activeFinlifeKeys.add(productKey);

            Set<String> tags = buildTags(product);
            String summary = buildSummary(product);
            String officialUrl = productUrlOverrideService.resolveOfficialUrl(
                productKey,
                "ACCOUNT",
                product.providerName(),
                product.productName(),
                resolveOfficialUrl(product.finCoNo(), companyUrls),
                officialUrlOverrides
            );

            Optional<AccountCatalogEntity> existing = accountCatalogRepository.findByProductKey(productKey);
            if (existing.isPresent()) {
                existing.get().refreshFromCatalog(
                    product.providerName(),
                    product.productName(),
                    product.accountKind(),
                    summary,
                    officialUrl,
                    tags,
                    true
                );
            } else {
                accountCatalogRepository.save(new AccountCatalogEntity(
                    productKey,
                    product.providerName(),
                    product.productName(),
                    product.accountKind(),
                    summary,
                    officialUrl,
                    true,
                    tags
                ));
            }
            upserted++;
        }

        int deactivated = 0;
        List<AccountCatalogEntity> previousFinlifeRows = accountCatalogRepository.findByProductKeyStartingWith(FINLIFE_KEY_PREFIX);
        for (AccountCatalogEntity entity : previousFinlifeRows) {
            if (!activeFinlifeKeys.contains(entity.getProductKey()) && entity.isActive()) {
                entity.deactivate();
                deactivated++;
            }
        }

        return new FinlifeSyncResponse(fetchedProducts.size(), upserted, deactivated, skipped);
    }

    @Transactional
    public CardExternalSyncResponse syncCardsFromExternal() {
        List<CardExternalApiClient.ExternalCardProduct> fetchedProducts = cardExternalApiClient.fetchCards();
        if (fetchedProducts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External card sync returned no products");
        }

        int upserted = 0;
        int skipped = 0;
        Set<String> activeKeys = new HashSet<>();
        Map<String, String> officialUrlOverrides = productUrlOverrideService.loadOverrides();

        for (CardExternalApiClient.ExternalCardProduct product : fetchedProducts) {
            String externalKey = safe(product.productKey());
            if (externalKey.isBlank() || safe(product.providerName()).isBlank() || safe(product.productName()).isBlank()) {
                skipped++;
                continue;
            }

            String productKey = CARD_EXTERNAL_KEY_PREFIX + sanitizeIdPart(externalKey);
            activeKeys.add(productKey);

            Set<String> tags = normalizeSet(product.tags());
            tags.add("external");

            Set<String> categories = normalizeSet(product.categories());
            if (categories.isEmpty()) {
                categories.add("online");
            }

            String annualFeeText = safe(product.annualFeeText()).isBlank()
                ? "연회비 정보 없음"
                : safe(product.annualFeeText());

            String summary = safe(product.summary()).isBlank()
                ? "외부 카드 데이터 동기화"
                : safe(product.summary());

            String officialUrl = productUrlOverrideService.resolveOfficialUrl(
                productKey,
                "CARD",
                safe(product.providerName()),
                safe(product.productName()),
                normalizeUrl(
                    safe(product.officialUrl()).isBlank() ? CARD_EXTERNAL_FALLBACK_URL : product.officialUrl(),
                    CARD_EXTERNAL_FALLBACK_URL
                ),
                officialUrlOverrides
            );

            Optional<CardCatalogEntity> existing = cardCatalogRepository.findByProductKey(productKey);
            if (existing.isPresent()) {
                existing.get().refreshFromCatalog(
                    safe(product.providerName()),
                    safe(product.productName()),
                    annualFeeText,
                    summary,
                    officialUrl,
                    tags,
                    categories,
                    true
                );
            } else {
                cardCatalogRepository.save(new CardCatalogEntity(
                    productKey,
                    safe(product.providerName()),
                    safe(product.productName()),
                    annualFeeText,
                    summary,
                    officialUrl,
                    true,
                    tags,
                    categories
                ));
            }

            upserted++;
        }

        int deactivated = 0;
        List<CardCatalogEntity> previousRows = cardCatalogRepository.findByProductKeyStartingWith(CARD_EXTERNAL_KEY_PREFIX);
        for (CardCatalogEntity entity : previousRows) {
            if (!activeKeys.contains(entity.getProductKey()) && entity.isActive()) {
                entity.deactivate();
                deactivated++;
            }
        }

        return new CardExternalSyncResponse(fetchedProducts.size(), upserted, deactivated, skipped);
    }

    private Map<String, String> fetchCompanyHomeUrls(List<String> topGroups) {
        Map<String, String> urls = new HashMap<>();
        for (String topGroup : topGroups) {
            int pageNo = 1;
            while (true) {
                JsonNode result = finlifeApiClient.fetchResult("companySearch.json", topGroup, pageNo);
                JsonNode baseList = result.path("baseList");

                if (baseList.isArray()) {
                    for (JsonNode company : baseList) {
                        String finCoNo = text(company, "fin_co_no");
                        if (finCoNo.isBlank()) {
                            continue;
                        }

                        String homeUrl = firstNonBlank(
                            text(company, "homp_url"),
                            text(company, "home_url")
                        );
                        if (!homeUrl.isBlank()) {
                            urls.put(finCoNo, normalizeUrl(homeUrl, FINLIFE_FALLBACK_URL));
                        }
                    }
                }

                int maxPageNo = parsePageNo(result.path("max_page_no"), pageNo);
                if (pageNo >= maxPageNo || isPaginationCapped(pageNo)) {
                    break;
                }
                pageNo++;
            }
        }
        return urls;
    }

    private List<FinlifeProduct> fetchProducts(List<String> topGroups) {
        List<FinlifeProduct> result = new ArrayList<>();
        result.addAll(fetchProductsByEndpoint("depositProductsSearch.json", "deposit", "예금", topGroups));
        result.addAll(fetchProductsByEndpoint("savingProductsSearch.json", "saving", "적금", topGroups));
        return result;
    }

    private List<FinlifeProduct> fetchProductsByEndpoint(
        String endpoint,
        String kindCode,
        String accountKind,
        List<String> topGroups
    ) {
        List<FinlifeProduct> rows = new ArrayList<>();

        for (String topGroup : topGroups) {
            int pageNo = 1;
            while (true) {
                JsonNode result = finlifeApiClient.fetchResult(endpoint, topGroup, pageNo);
                JsonNode baseList = result.path("baseList");
                JsonNode optionList = result.path("optionList");

                Map<String, RateSummary> rateSummaryByProduct = summarizeRates(optionList);

                if (baseList.isArray()) {
                    for (JsonNode base : baseList) {
                        String finCoNo = text(base, "fin_co_no");
                        String productCode = text(base, "fin_prdt_cd");
                        String providerName = text(base, "kor_co_nm");
                        String productName = text(base, "fin_prdt_nm");
                        String joinWay = text(base, "join_way");
                        String specialCondition = text(base, "spcl_cnd");
                        String etcNote = text(base, "etc_note");

                        String keyWithCoNo = composeRateKey(finCoNo, productCode);
                        String keyWithoutCoNo = composeRateKey("", productCode);
                        RateSummary rateSummary = Optional.ofNullable(rateSummaryByProduct.get(keyWithCoNo))
                            .orElse(rateSummaryByProduct.getOrDefault(keyWithoutCoNo, RateSummary.empty()));

                        rows.add(new FinlifeProduct(
                            kindCode,
                            accountKind,
                            finCoNo,
                            productCode,
                            providerName,
                            productName,
                            joinWay,
                            specialCondition,
                            etcNote,
                            rateSummary.maxBaseRate(),
                            rateSummary.maxPreferRate()
                        ));
                    }
                }

                int maxPageNo = parsePageNo(result.path("max_page_no"), pageNo);
                if (pageNo >= maxPageNo || isPaginationCapped(pageNo)) {
                    break;
                }
                pageNo++;
            }
        }

        return rows;
    }

    private Map<String, RateSummary> summarizeRates(JsonNode optionList) {
        Map<String, RateSummary> summaries = new HashMap<>();
        if (!optionList.isArray()) {
            return summaries;
        }

        for (JsonNode option : optionList) {
            String key = composeRateKey(text(option, "fin_co_no"), text(option, "fin_prdt_cd"));
            if (key.isBlank()) {
                continue;
            }

            double baseRate = parseRate(option.path("intr_rate"));
            double preferRate = parseRate(option.path("intr_rate2"));
            RateSummary previous = summaries.getOrDefault(key, RateSummary.empty());
            summaries.put(key, new RateSummary(
                Math.max(previous.maxBaseRate(), baseRate),
                Math.max(previous.maxPreferRate(), preferRate)
            ));

            String fallbackKey = composeRateKey("", text(option, "fin_prdt_cd"));
            RateSummary fallbackPrevious = summaries.getOrDefault(fallbackKey, RateSummary.empty());
            summaries.put(fallbackKey, new RateSummary(
                Math.max(fallbackPrevious.maxBaseRate(), baseRate),
                Math.max(fallbackPrevious.maxPreferRate(), preferRate)
            ));
        }
        return summaries;
    }

    private Set<String> buildTags(FinlifeProduct product) {
        Set<String> tags = new HashSet<>();
        tags.add("finlife");
        tags.add("savings");

        if ("적금".equals(product.accountKind())) {
            tags.add("goal");
        }

        String productText = (product.productName() + " " + product.specialCondition() + " " + product.etcNote())
            .toLowerCase(Locale.ROOT);
        String joinWay = product.joinWay().toLowerCase(Locale.ROOT);

        if (containsAny(productText, "급여", "salary")) {
            tags.add("salary");
            tags.add("daily");
        }
        if (containsAny(productText, "청년", "young")) {
            tags.add("young");
        }
        if (containsAny(productText, "자동이체", "auto")) {
            tags.add("auto");
            tags.add("daily");
        }
        if (containsAny(joinWay, "인터넷", "스마트폰", "모바일", "비대면")) {
            tags.add("starter");
            tags.add("daily");
        }

        return tags;
    }

    private String buildSummary(FinlifeProduct product) {
        String rateText;
        if (product.maxPreferRate() > 0) {
            rateText = "최고 " + formatRate(product.maxPreferRate())
                + "% (기본 " + formatRate(product.maxBaseRate()) + "%)";
        } else if (product.maxBaseRate() > 0) {
            rateText = "기본 금리 " + formatRate(product.maxBaseRate()) + "%";
        } else {
            rateText = "금리 정보는 상세 페이지에서 확인";
        }

        String conditionText = firstNonBlank(
            normalizeSummarySource(product.specialCondition()),
            normalizeSummarySource(product.etcNote()),
            "우대조건은 상품설명서를 확인"
        );

        return rateText + " · " + conditionText;
    }

    private String resolveOfficialUrl(String finCoNo, Map<String, String> companyUrls) {
        String resolved = companyUrls.getOrDefault(finCoNo, FINLIFE_FALLBACK_URL);
        return normalizeUrl(resolved, FINLIFE_FALLBACK_URL);
    }

    private String normalizeUrl(String value, String fallbackUrl) {
        String text = safe(value);
        if (text.isBlank()) {
            return fallbackUrl;
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text;
        }
        return "https://" + text;
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSummarySource(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
    }

    private String composeRateKey(String finCoNo, String productCode) {
        String normalizedCode = safe(productCode);
        if (normalizedCode.isBlank()) {
            return "";
        }
        return sanitizeIdPart(finCoNo) + "|" + sanitizeIdPart(normalizedCode);
    }

    private String sanitizeIdPart(String value) {
        String normalized = safe(value);
        if (normalized.isBlank()) {
            return "na";
        }
        return normalized.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]", "-");
    }

    private List<String> sanitizeTopGroups(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(this::safe)
            .flatMap(value -> List.of(value.split(",")).stream())
            .map(this::safe)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private Set<String> normalizeSet(Set<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }

        for (String value : values) {
            String normalized = safe(value).toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private boolean isPaginationCapped(int currentPageNo) {
        int cap = finlifeProperties.getMaxPagesPerGroup();
        return cap > 0 && currentPageNo >= cap;
    }

    private int parsePageNo(JsonNode pageNoNode, int fallback) {
        String raw = pageNoNode == null ? "" : pageNoNode.asText("").trim();
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(Integer.parseInt(raw), 1);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double parseRate(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }

        String raw = node.asText("").trim();
        if (raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return 0;
        }

        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String formatRate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return "";
        }
        return safe(node.path(fieldName).asText(""));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record RateSummary(double maxBaseRate, double maxPreferRate) {

        private static RateSummary empty() {
            return new RateSummary(0, 0);
        }
    }

    private record FinlifeProduct(
        String kindCode,
        String accountKind,
        String finCoNo,
        String productCode,
        String providerName,
        String productName,
        String joinWay,
        String specialCondition,
        String etcNote,
        double maxBaseRate,
        double maxPreferRate
    ) {
    }
}
