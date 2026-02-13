package com.benepick.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
public class CardExternalApiClient {

    private static final Logger log = LoggerFactory.getLogger(CardExternalApiClient.class);

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final CardExternalProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectMapper xmlMapper;
    private final HttpClient httpClient;
    private final Set<String> fallbackItemsPathWarned = new HashSet<>();

    public CardExternalApiClient(CardExternalProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.xmlMapper = initXmlMapperOrNull();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1000)))
            .build();
    }

    public List<ExternalCardProduct> fetchCards() {
        String mode = normalizeMode(properties.getMode());

        if ("public-data-all".equals(mode)) {
            return fetchCardsFromPublicDataAll();
        }

        if ("public-data".equals(mode)) {
            return fetchCardsFromPublicDataSingle();
        }

        if ("source".equals(mode) || mode.isBlank()) {
            return fetchCardsFromSource();
        }

        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Unsupported CARD_EXTERNAL_MODE: " + mode + " (allowed: source, public-data, public-data-all)"
        );
    }

    private List<ExternalCardProduct> fetchCardsFromSource() {
        String source = safe(properties.getSourceUrl());
        if (source.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CARD_EXTERNAL_SOURCE_URL is not configured");
        }

        String body = loadBody(source);
        JsonNode root = parseStructuredBody(body, "external card source");
        JsonNode rows = resolveRows(root);

        List<ExternalCardProduct> products = mapRowsToProducts(
            rows,
            "external",
            "",
            "",
            setOf("external"),
            Collections.emptySet(),
            "외부 카드 데이터 동기화"
        );

        return deduplicateProducts(products);
    }

    private List<ExternalCardProduct> fetchCardsFromPublicDataSingle() {
        CardExternalProperties.PublicData config = properties.getPublicData();
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalog.card-external.public-data is missing");
        }

        String url = safe(config.getUrl());
        if (url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CARD_PUBLIC_DATA_URL is not configured");
        }

        String serviceKey = safe(config.getServiceKey());
        if (serviceKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CARD_PUBLIC_DATA_SERVICE_KEY is not configured");
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put(firstNonBlank(config.getServiceKeyParam(), "serviceKey"), serviceKey);
        query.put("pageNo", config.getPageNo());
        query.put("numOfRows", config.getNumOfRows());

        if (config.isForceJson()) {
            query.put("_type", "json");
            query.put("resultType", "json");
        }

        appendExtraQuery(query, config.getExtraQuery());

        JsonNode rows = fetchPublicDataRows(url, query, config.getItemsPath(), "public-data single source");

        List<ExternalCardProduct> products = mapRowsToProducts(
            rows,
            "public-single",
            config.getDefaultProviderName(),
            config.getOfficialUrlFallback(),
            splitCsvToLowerSet(config.getDefaultTags()),
            splitCsvToLowerSet(config.getDefaultCategories()),
            "공공데이터 카드 소스 동기화"
        );

        return deduplicateProducts(products);
    }

    private List<ExternalCardProduct> fetchCardsFromPublicDataAll() {
        CardExternalProperties.PublicDataAll all = properties.getPublicDataAll();
        if (all == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalog.card-external.public-data-all is missing");
        }

        String serviceKey = firstNonBlank(all.getServiceKey(), properties.getPublicData().getServiceKey());
        if (serviceKey.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "CARD_PUBLIC_ALL_SERVICE_KEY or CARD_PUBLIC_DATA_SERVICE_KEY is not configured"
            );
        }

        List<ExternalCardProduct> merged = new ArrayList<>();
        Map<String, String> sourceErrors = new LinkedHashMap<>();

        if (all.isIncludeKdb()) {
            try {
                List<ExternalCardProduct> kdb = fetchKdbCards(all.getKdb(), serviceKey);
                if (kdb.isEmpty()) {
                    sourceErrors.put("kdb", "empty result");
                }
                merged.addAll(kdb);
            } catch (Exception exception) {
                sourceErrors.put("kdb", exception.getMessage());
                log.warn("KDB card source sync skipped: {}", exception.getMessage());
            }
        } else {
            sourceErrors.put("kdb", "disabled by config");
        }

        if (all.isIncludeKrpost()) {
            try {
                List<ExternalCardProduct> krpost = fetchKrpostCards(all.getKrpost(), serviceKey);
                if (krpost.isEmpty()) {
                    sourceErrors.put("krpost", "empty result");
                }
                merged.addAll(krpost);
            } catch (Exception exception) {
                sourceErrors.put("krpost", exception.getMessage());
                log.warn("KRPOST card source sync skipped: {}", exception.getMessage());
            }
        } else {
            sourceErrors.put("krpost", "disabled by config");
        }

        if (all.isIncludeFinanceStats()) {
            try {
                List<ExternalCardProduct> finStats = fetchFinanceStatsCards(all.getFinanceStats(), serviceKey);
                if (finStats.isEmpty()) {
                    sourceErrors.put("finance-stats", "empty result");
                }
                merged.addAll(finStats);
            } catch (Exception exception) {
                sourceErrors.put("finance-stats", exception.getMessage());
                log.warn("Finance stats card source sync skipped: {}", exception.getMessage());
            }
        } else {
            sourceErrors.put("finance-stats", "disabled by config");
        }

        List<ExternalCardProduct> deduplicated = deduplicateProducts(merged);
        if (deduplicated.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "All public card sources returned empty result: " + sourceErrors
            );
        }

        return deduplicated;
    }

    private List<ExternalCardProduct> fetchKdbCards(CardExternalProperties.Kdb config, String serviceKey) {
        if (config == null || safe(config.getUrl()).isBlank()) {
            return List.of();
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("serviceKey", serviceKey);
        query.put("pageNo", config.getPageNo());
        query.put("numOfRows", config.getNumOfRows());
        query.put("sBseDt", config.getStartDate());
        query.put("eBseDt", config.getEndDate());

        if (config.isForceJson()) {
            query.put("_type", "json");
            query.put("resultType", "json");
        }

        JsonNode rows = fetchPublicDataRowsPaged(
            config.getUrl(),
            query,
            config.getItemsPath(),
            "KDB card product source",
            config.getMaxPages()
        );

        return mapRowsToProducts(
            rows,
            "public-kdb",
            config.getDefaultProviderName(),
            config.getOfficialUrlFallback(),
            setOf("external", "cashback", "daily"),
            setOf("online", "transport"),
            "한국산업은행 카드상품 데이터"
        );
    }

    private List<ExternalCardProduct> fetchKrpostCards(CardExternalProperties.Krpost config, String serviceKey) {
        if (config == null || safe(config.getUrl()).isBlank()) {
            return List.of();
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("serviceKey", serviceKey);
        query.put("GDS_NM", firstNonBlank(config.getProductNameKeyword(), "브라보"));
        query.put("pageNo", config.getPageNo());
        query.put("numOfRows", config.getNumOfRows());

        JsonNode rows = fetchPublicDataRowsPaged(
            config.getUrl(),
            query,
            config.getItemsPath(),
            "KRPOST card product source",
            config.getMaxPages()
        );

        return mapRowsToProducts(
            rows,
            "public-krpost",
            config.getDefaultProviderName(),
            config.getOfficialUrlFallback(),
            setOf("external", "starter", "daily"),
            setOf("transport"),
            "우체국 체크카드상품 데이터"
        );
    }

    private List<ExternalCardProduct> fetchFinanceStatsCards(CardExternalProperties.FinanceStats config, String serviceKey) {
        if (config == null || safe(config.getUrl()).isBlank()) {
            return List.of();
        }

        String basYm = safe(config.getBaseYearMonth());
        if (basYm.isBlank()) {
            basYm = YearMonth.now().minusMonths(1).format(YEAR_MONTH_FORMATTER);
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("serviceKey", serviceKey);
        query.put("numOfRows", config.getNumOfRows());
        query.put("pageNo", config.getPageNo());
        query.put("resultType", firstNonBlank(config.getResultType(), "json"));
        query.put("title", firstNonBlank(config.getTitle(), "신용카드_일반현황_임직원현황"));
        query.put("basYm", basYm);

        JsonNode rows = fetchPublicDataRowsPaged(
            config.getUrl(),
            query,
            config.getItemsPath(),
            "Finance committee card stats source",
            config.getMaxPages()
        );

        return mapRowsToProducts(
            rows,
            "public-finstat",
            config.getDefaultProviderName(),
            config.getOfficialUrlFallback(),
            setOf("external", "stat-only"),
            Collections.emptySet(),
            "신용카드사 통계 데이터"
        );
    }

    private JsonNode fetchPublicDataRows(
        String url,
        Map<String, String> query,
        String itemsPath,
        String sourceLabel
    ) {
        URI uri = buildUri(url, query);
        String body = fetchRemote(uri);
        JsonNode root = parseStructuredBody(body, sourceLabel);
        validatePublicDataResponse(root, sourceLabel);
        return resolvePublicDataRows(root, itemsPath);
    }

    private JsonNode fetchPublicDataRowsPaged(
        String url,
        Map<String, String> query,
        String itemsPath,
        String sourceLabel,
        int maxPages
    ) {
        int startPage = parsePositiveInt(query.get("pageNo"), 1);
        int numOfRows = parsePositiveInt(query.get("numOfRows"), 100);
        int pageLimit = Math.max(maxPages, 1);

        ArrayNode mergedRows = objectMapper.createArrayNode();

        for (int offset = 0; offset < pageLimit; offset++) {
            int currentPage = startPage + offset;
            Map<String, String> pagedQuery = new LinkedHashMap<>(query);
            pagedQuery.put("pageNo", String.valueOf(currentPage));

            URI uri = buildUri(url, pagedQuery);
            String body = fetchRemote(uri);
            JsonNode root = parseStructuredBody(body, sourceLabel);
            validatePublicDataResponse(root, sourceLabel);

            JsonNode rows = resolvePublicDataRows(root, itemsPath);
            appendRows(mergedRows, rows);

            int fetchedCount = rows.isArray() ? rows.size() : 0;
            int totalCount = extractTotalCount(root);

            if (fetchedCount <= 0) {
                break;
            }

            if (fetchedCount < numOfRows) {
                break;
            }

            if (totalCount > 0 && mergedRows.size() >= totalCount) {
                break;
            }

            if (totalCount > 0 && ((long) currentPage * numOfRows) >= totalCount) {
                break;
            }
        }

        return mergedRows;
    }

    private int extractTotalCount(JsonNode root) {
        String raw = firstNonBlank(
            text(root.path("response").path("body").path("items"), "totalCount"),
            text(root.path("response").path("body"), "totalCount"),
            text(root.path("body"), "totalCount"),
            text(root, "totalCount")
        );
        return parsePositiveInt(raw, -1);
    }

    private int parsePositiveInt(String value, int fallback) {
        String normalized = safe(value);
        if (normalized.isBlank()) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(normalized);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void appendRows(ArrayNode target, JsonNode rows) {
        if (rows == null || rows.isMissingNode() || rows.isNull()) {
            return;
        }

        if (rows.isArray()) {
            for (JsonNode row : rows) {
                target.add(row);
            }
            return;
        }

        if (rows.isObject()) {
            target.add(rows);
        }
    }

    private List<ExternalCardProduct> mapRowsToProducts(
        JsonNode rows,
        String keyPrefix,
        String defaultProviderName,
        String officialUrlFallback,
        Set<String> defaultTags,
        Set<String> defaultCategories,
        String defaultSummary
    ) {
        List<ExternalCardProduct> products = new ArrayList<>();

        for (JsonNode row : rows) {
            String providerName = firstNonBlank(
                text(row, "providerName"),
                text(row, "provider"),
                text(row, "company"),
                text(row, "cardCoNm"),
                text(row, "cardCompanyName"),
                text(row, "cardIssrNm"),
                text(row, "cmpyNm"),
                text(row, "bankName"),
                text(row, "fncoNm"),
                text(row, "fncNm"),
                defaultProviderName
            );

            String productName = firstNonBlank(
                text(row, "productName"),
                text(row, "name"),
                text(row, "cardPrdNm"),
                text(row, "cardNm"),
                text(row, "prdNm"),
                text(row, "finPrdtNm"),
                text(row, "GDS_NM"),
                text(row, "title")
            );

            if (providerName.isBlank() || productName.isBlank()) {
                continue;
            }

            String productKey = firstNonBlank(
                text(row, "productKey"),
                text(row, "product_id"),
                text(row, "code"),
                text(row, "cardPrdId"),
                text(row, "cardPrdCd"),
                text(row, "id"),
                text(row, "GDS_CD"),
                text(row, "gdsCd"),
                text(row, "fncoCd"),
                text(row, "crno")
            );

            if (productKey.isBlank()) {
                productKey = generateProductKey(keyPrefix, providerName, productName);
            }

            String annualFeeText = firstNonBlank(
                text(row, "annualFeeText"),
                text(row, "annualFee"),
                text(row, "annlFee"),
                text(row, "annFee"),
                text(row, "cardFee"),
                text(row, "fee"),
                text(row, "anmfOtl"),
                "연회비 정보 없음"
            );

            String summary = buildSummaryFromRow(row, defaultSummary);

            String officialUrl = firstNonBlank(
                text(row, "officialUrl"),
                text(row, "url"),
                text(row, "link"),
                text(row, "homepageUrl"),
                text(row, "hompUrl"),
                text(row, "CCRD_URL_S50"),
                officialUrlFallback
            );

            Set<String> tags = new HashSet<>(defaultTags);
            tags.addAll(parseStringSet(
                row.path("tags"),
                row.path("tagCodes"),
                row.path("benefitType"),
                row.path("bnftType")
            ));

            Set<String> categories = new HashSet<>(defaultCategories);
            categories.addAll(parseStringSet(
                row.path("categories"),
                row.path("categoryCodes"),
                row.path("benefitCategory"),
                row.path("bnftCategory")
            ));

            products.add(new ExternalCardProduct(
                productKey,
                providerName,
                productName,
                annualFeeText,
                summary,
                officialUrl,
                tags,
                categories
            ));
        }

        return products;
    }

    private List<ExternalCardProduct> deduplicateProducts(List<ExternalCardProduct> products) {
        Map<String, ExternalCardProduct> deduplicated = new LinkedHashMap<>();

        for (ExternalCardProduct product : products) {
            if (product == null || safe(product.productKey()).isBlank()) {
                continue;
            }
            deduplicated.put(product.productKey(), product);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private String buildSummaryFromRow(JsonNode row, String defaultSummary) {
        String explicit = firstNonBlank(
            text(row, "summary"),
            text(row, "description"),
            text(row, "benefitSummary"),
            text(row, "bnftSmry"),
            text(row, "cardDesc"),
            text(row, "prdtFeature"),
            text(row, "prdOtl")
        );
        if (!explicit.isBlank()) {
            return explicit;
        }

        List<String> parts = new ArrayList<>();
        addSummaryPart(parts, "가입대상", firstNonBlank(text(row, "PPSN_CORP_DVSN_NM_S10"), text(row, "jinTgtCone")));
        addSummaryPart(parts, "발급가능", text(row, "CARD_ISSU_PSBL_YN_S10"));
        addSummaryPart(parts, "교통카드", firstNonBlank(text(row, "TRFC_CARD_DVSN_NM_S20"), text(row, "cadTpTcNm")));
        addSummaryPart(parts, "해외이용", firstNonBlank(text(row, "FORN_USE_PSBL_YN_S30"), text(row, "frnUseYn")));
        addSummaryPart(parts, "상품개요", firstNonBlank(text(row, "prdOtl"), text(row, "benefit"), text(row, "prdtFeature")));

        if (!parts.isEmpty()) {
            return String.join(" · ", parts);
        }

        return defaultSummary;
    }

    private void addSummaryPart(List<String> parts, String label, String value) {
        String normalized = safe(value);
        if (!normalized.isBlank()) {
            parts.add(label + ": " + normalized);
        }
    }

    private URI buildUri(String baseUrl, Map<String, String> query) {
        String normalizedBase = safe(baseUrl);
        if (normalizedBase.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public data URL is empty");
        }

        StringBuilder builder = new StringBuilder(normalizedBase);
        boolean hasQuery = normalizedBase.contains("?");

        for (Map.Entry<String, String> entry : query.entrySet()) {
            String key = safe(entry.getKey());
            String value = safe(entry.getValue());
            if (key.isBlank() || value.isBlank()) {
                continue;
            }

            if (!hasQuery) {
                builder.append('?');
                hasQuery = true;
            } else if (builder.charAt(builder.length() - 1) != '?' && builder.charAt(builder.length() - 1) != '&') {
                builder.append('&');
            }

            builder.append(encode(key)).append('=').append(encode(value));
        }

        return URI.create(builder.toString());
    }

    private void appendExtraQuery(Map<String, String> query, String extraQuery) {
        String normalized = safe(extraQuery);
        if (normalized.isBlank()) {
            return;
        }

        if (normalized.startsWith("?")) {
            normalized = normalized.substring(1);
        }

        for (String token : normalized.split("&")) {
            String part = safe(token);
            if (part.isBlank()) {
                continue;
            }

            int equalIndex = part.indexOf('=');
            if (equalIndex >= 0) {
                query.put(part.substring(0, equalIndex), part.substring(equalIndex + 1));
            } else {
                query.put(part, "");
            }
        }
    }

    private String loadBody(String source) {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return fetchRemote(URI.create(source));
        }
        return readLocal(source);
    }

    private String fetchRemote(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 2000)))
            .header("Accept", "application/json, application/xml, text/xml, */*")
            .header("User-Agent", "benepick-backend/1.0")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to fetch external card source: " + exception.getMessage(),
                exception
            );
        }

        if (response.statusCode() != 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "External card source returned status " + response.statusCode()
            );
        }

        if (response.body() == null || response.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External card source returned empty body");
        }

        return response.body();
    }

    private String readLocal(String source) {
        Path path = resolvePath(source);
        try {
            String body = Files.readString(path, StandardCharsets.UTF_8);
            if (body.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External card source file is empty");
            }
            return body;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to read external card source file: " + exception.getMessage(),
                exception
            );
        }
    }

    private Path resolvePath(String source) {
        if (source.startsWith("file://")) {
            try {
                return Path.of(new URI(source));
            } catch (URISyntaxException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file URI for external card source");
            }
        }
        return Path.of(source);
    }

    private ObjectMapper initXmlMapperOrNull() {
        try {
            Class<?> xmlMapperClass = Class.forName("com.fasterxml.jackson.dataformat.xml.XmlMapper");
            Object instance = xmlMapperClass.getDeclaredConstructor().newInstance();
            if (instance instanceof ObjectMapper mapper) {
                return mapper;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private JsonNode parseStructuredBody(String body, String sourceLabel) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException jsonException) {
            if (xmlMapper != null) {
                try {
                    return xmlMapper.readTree(body);
                } catch (IOException ignored) {
                    // Fall through to JDK XML parsing fallback.
                }
            }

            JsonNode fallback = parseXmlWithJdk(body);
            if (fallback != null) {
                return fallback;
            }

            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to parse " + sourceLabel + " response as JSON/XML",
                jsonException
            );
        }
    }

    private JsonNode parseXmlWithJdk(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            } catch (Exception ignored) {
                // Optional hardening may not be supported by all XML parser implementations.
            }

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(body)));
            Element root = document.getDocumentElement();
            if (root == null) {
                return null;
            }

            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set(root.getNodeName(), xmlElementToJson(root));
            return wrapper;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode xmlElementToJson(Element element) {
        NodeList children = element.getChildNodes();
        boolean hasElementChild = false;
        ObjectNode objectNode = objectMapper.createObjectNode();

        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            hasElementChild = true;
            String fieldName = node.getNodeName();
            JsonNode childJson = xmlElementToJson((Element) node);
            JsonNode existing = objectNode.get(fieldName);
            if (existing == null) {
                objectNode.set(fieldName, childJson);
            } else if (existing.isArray()) {
                ((ArrayNode) existing).add(childJson);
            } else {
                ArrayNode arrayNode = objectMapper.createArrayNode();
                arrayNode.add(existing);
                arrayNode.add(childJson);
                objectNode.set(fieldName, arrayNode);
            }
        }

        if (!hasElementChild) {
            return objectMapper.getNodeFactory().textNode(safe(element.getTextContent()));
        }

        return objectNode;
    }
    private void validatePublicDataResponse(JsonNode root, String sourceLabel) {
        String resultCode = firstNonBlank(
            text(root.path("response").path("header"), "resultCode"),
            text(root.path("header"), "resultCode"),
            text(root, "resultCode")
        );

        if (resultCode.isBlank()) {
            return;
        }

        String normalizedCode = resultCode.trim();
        if ("0".equals(normalizedCode)
            || "00".equals(normalizedCode)
            || "000".equals(normalizedCode)
            || "NORMAL_CODE".equalsIgnoreCase(normalizedCode)) {
            return;
        }

        String resultMessage = firstNonBlank(
            text(root.path("response").path("header"), "resultMsg"),
            text(root.path("header"), "resultMsg"),
            text(root, "resultMsg")
        );

        String detail = resultMessage.isBlank() ? "unknown error" : resultMessage;
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            sourceLabel + " API error (" + resultCode + "): " + detail
        );
    }

    private JsonNode resolveRows(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External card source JSON is empty");
        }

        if (root.isArray()) {
            return root;
        }

        JsonNode cards = root.path("cards");
        if (cards.isArray()) {
            return cards;
        }

        JsonNode data = root.path("data");
        if (data.isArray()) {
            return data;
        }

        JsonNode items = root.path("items");
        if (items.isArray()) {
            return items;
        }

        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "External card source JSON must be an array or include cards/data/items array"
        );
    }

    private JsonNode resolvePublicDataRows(JsonNode root, String itemsPath) {
        if (root == null || root.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Public data API response is empty");
        }

        if (root.isArray()) {
            return root;
        }

        if (!safe(itemsPath).isBlank()) {
            JsonNode selected = selectPath(root, itemsPath);
            JsonNode normalized = normalizeRowsNode(selected);
            if (normalized != null) {
                return normalized;
            }

            // Fallback to built-in candidates when configured path mismatches
            if (fallbackItemsPathWarned.add(itemsPath)) {
                log.warn("Configured itemsPath {} not matched. Falling back to default path candidates", itemsPath);
            }
        }

        List<JsonNode> candidates = List.of(
            root.path("response").path("body").path("items").path("item"),
            root.path("response").path("body").path("items"),
            root.path("response").path("body").path("tableList").path(0).path("items").path("item"),
            root.path("response").path("body").path("tableList").path(0).path("items"),
            root.path("response").path("body").path("tableList").path("items").path("item"),
            root.path("body").path("items").path("item"),
            root.path("body").path("items"),
            root.path("items").path("item"),
            root.path("items"),
            root.path("data"),
            root.path("cards"),
            root.path("result").path("items")
        );

        for (JsonNode candidate : candidates) {
            JsonNode normalized = normalizeRowsNode(candidate);
            if (normalized != null) {
                return normalized;
            }
        }

        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Public data response rows not found. Configure CARD_PUBLIC_DATA_ITEMS_PATH if needed"
        );
    }

    private JsonNode selectPath(JsonNode root, String pathExpression) {
        JsonNode current = root;
        for (String token : pathExpression.split("\\.")) {
            String field = safe(token);
            if (field.isBlank()) {
                continue;
            }
            current = applyPathToken(current, field);
        }
        return current;
    }

    private JsonNode applyPathToken(JsonNode current, String token) {
        if (current == null || current.isMissingNode() || current.isNull()) {
            return current;
        }

        String remaining = safe(token);
        while (!remaining.isBlank()) {
            int openBracket = remaining.indexOf('[');
            if (openBracket < 0) {
                return pathByFieldOrIndex(current, remaining);
            }

            String fieldPart = remaining.substring(0, openBracket).trim();
            if (!fieldPart.isBlank()) {
                current = pathByFieldOrIndex(current, fieldPart);
            }

            int closeBracket = remaining.indexOf(']', openBracket);
            if (closeBracket < 0) {
                return pathByFieldOrIndex(current, remaining);
            }

            String indexPart = remaining.substring(openBracket + 1, closeBracket).trim();
            current = pathByFieldOrIndex(current, indexPart);
            remaining = remaining.substring(closeBracket + 1).trim();
        }

        return current;
    }

    private JsonNode pathByFieldOrIndex(JsonNode current, String segment) {
        if (current == null || current.isMissingNode() || current.isNull()) {
            return current;
        }

        String normalized = safe(segment);
        if (normalized.isBlank()) {
            return current;
        }

        if (isNumeric(normalized)) {
            try {
                return current.path(Integer.parseInt(normalized));
            } catch (NumberFormatException ignored) {
                // no-op
            }
        }

        return current.path(normalized);
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }

        return true;
    }

    private JsonNode normalizeRowsNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isArray()) {
            return node;
        }

        if (node.isObject()) {
            JsonNode itemArray = node.path("item");
            if (itemArray.isArray()) {
                return itemArray;
            }

            JsonNode dataArray = node.path("data");
            if (dataArray.isArray()) {
                return dataArray;
            }

            ArrayNode wrapped = objectMapper.createArrayNode();
            wrapped.add(node);
            return wrapped;
        }

        return null;
    }

    private Set<String> parseStringSet(JsonNode... candidates) {
        Set<String> values = new HashSet<>();
        for (JsonNode candidate : candidates) {
            if (candidate == null || candidate.isMissingNode() || candidate.isNull()) {
                continue;
            }

            if (candidate.isArray()) {
                for (JsonNode item : candidate) {
                    String value = normalize(item.asText(""));
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
                continue;
            }

            String raw = candidate.asText("");
            if (!raw.isBlank()) {
                for (String part : raw.split(",")) {
                    String value = normalize(part);
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
            }
        }

        return values;
    }

    private Set<String> splitCsvToLowerSet(String csv) {
        Set<String> values = new HashSet<>();
        String raw = safe(csv);
        if (raw.isBlank()) {
            return values;
        }

        for (String part : raw.split(",")) {
            String value = normalize(part);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private Set<String> setOf(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = safe(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return "";
        }
        return safe(node.path(fieldName).asText(""));
    }

    private String normalizeMode(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String generateProductKey(String prefix, String providerName, String productName) {
        String slug = toSlug(providerName + "-" + productName);
        if (slug.isBlank()) {
            slug = "card";
        }
        String stableHash = Integer.toUnsignedString((providerName + "|" + productName).hashCode(), 16);
        return prefix + "-" + slug + "-" + stableHash;
    }

    private String toSlug(String value) {
        String lowered = safe(value).toLowerCase(Locale.ROOT);
        String slug = lowered.replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return slug;
    }

    public record ExternalCardProduct(
        String productKey,
        String providerName,
        String productName,
        String annualFeeText,
        String summary,
        String officialUrl,
        Set<String> tags,
        Set<String> categories
    ) {
    }
}
