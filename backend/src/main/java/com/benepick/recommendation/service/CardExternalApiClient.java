package com.benepick.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CardExternalApiClient {

    private final CardExternalProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CardExternalApiClient(CardExternalProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1000)))
            .build();
    }

    public List<ExternalCardProduct> fetchCards() {
        String source = safe(properties.getSourceUrl());
        if (source.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CARD_EXTERNAL_SOURCE_URL is not configured");
        }

        String body = loadBody(source);
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to parse external card source JSON",
                exception
            );
        }

        JsonNode rows = resolveRows(root);
        List<ExternalCardProduct> products = new ArrayList<>();
        for (JsonNode row : rows) {
            products.add(new ExternalCardProduct(
                firstNonBlank(text(row, "productKey"), text(row, "product_id"), text(row, "code")),
                firstNonBlank(text(row, "providerName"), text(row, "provider"), text(row, "company")),
                firstNonBlank(text(row, "productName"), text(row, "name")),
                firstNonBlank(text(row, "annualFeeText"), text(row, "annualFee"), "연회비 정보 없음"),
                firstNonBlank(text(row, "summary"), text(row, "description"), "외부 카드 데이터 동기화"),
                firstNonBlank(text(row, "officialUrl"), text(row, "url"), text(row, "link")),
                parseStringSet(row.path("tags"), row.path("tagCodes")),
                parseStringSet(row.path("categories"), row.path("categoryCodes"))
            ));
        }

        return products;
    }

    private String loadBody(String source) {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return fetchRemote(source);
        }
        return readLocal(source);
    }

    private String fetchRemote(String source) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source))
            .GET()
            .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 2000)))
            .header("Accept", "application/json")
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

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
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
