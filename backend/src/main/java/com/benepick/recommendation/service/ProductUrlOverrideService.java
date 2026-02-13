package com.benepick.recommendation.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProductUrlOverrideService {

    private static final Logger log = LoggerFactory.getLogger(ProductUrlOverrideService.class);

    private final Path overrideFilePath;

    public ProductUrlOverrideService(
        @Value("${catalog.product-url-overrides.path:./config/product-url-overrides.properties}") String overrideFilePath
    ) {
        this.overrideFilePath = Path.of(overrideFilePath).toAbsolutePath().normalize();
    }

    public Map<String, String> loadOverrides() {
        if (!Files.exists(overrideFilePath)) {
            return Map.of();
        }

        Map<String, String> overrides = new HashMap<>();

        try {
            for (String rawLine : Files.readAllLines(overrideFilePath, StandardCharsets.UTF_8)) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0 || separatorIndex >= line.length() - 1) {
                    continue;
                }

                String overrideKey = normalizeOverrideKey(line.substring(0, separatorIndex));
                String overrideUrl = normalizeUrl(line.substring(separatorIndex + 1));
                if (!overrideKey.isBlank() && !overrideUrl.isBlank()) {
                    overrides.put(overrideKey, overrideUrl);
                }
            }
        } catch (IOException exception) {
            log.warn("Failed to read product URL override file: {}", overrideFilePath, exception);
            return Map.of();
        }

        return overrides;
    }

    public String resolveOfficialUrl(String productKey, String fallbackUrl, Map<String, String> overrideMap) {
        return resolveOfficialUrl(productKey, "", "", "", fallbackUrl, overrideMap);
    }

    public String resolveOfficialUrl(
        String productKey,
        String productType,
        String providerName,
        String productName,
        String fallbackUrl,
        Map<String, String> overrideMap
    ) {
        if (overrideMap == null || overrideMap.isEmpty()) {
            return normalizeUrl(fallbackUrl);
        }

        for (String lookupKey : buildLookupKeys(productKey, productType, providerName, productName)) {
            String overrideUrl = overrideMap.get(lookupKey);
            if (overrideUrl != null && !overrideUrl.isBlank()) {
                return overrideUrl;
            }
        }

        return normalizeUrl(fallbackUrl);
    }

    private List<String> buildLookupKeys(
        String productKey,
        String productType,
        String providerName,
        String productName
    ) {
        List<String> keys = new ArrayList<>();

        String normalizedProductKey = normalizeOverrideKey(productKey);
        if (!normalizedProductKey.isBlank()) {
            keys.add(normalizedProductKey);
        }

        String normalizedType = normalizeOverrideToken(productType);
        String normalizedProvider = normalizeOverrideToken(providerName);
        String normalizedProductName = normalizeOverrideToken(productName);

        if (!normalizedType.isBlank() && !normalizedProvider.isBlank() && !normalizedProductName.isBlank()) {
            keys.add(normalizedType + "|" + normalizedProvider + "|" + normalizedProductName);
        }

        if (!normalizedProvider.isBlank() && !normalizedProductName.isBlank()) {
            keys.add(normalizedProvider + "|" + normalizedProductName);
        }

        if (!normalizedType.isBlank() && !normalizedProductName.isBlank()) {
            keys.add(normalizedType + "|" + normalizedProductName);
        }

        return keys;
    }

    private String normalizeOverrideKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }

        if (!normalized.contains("|")) {
            return normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        }

        StringBuilder builder = new StringBuilder();
        for (String token : normalized.split("\\|")) {
            String normalizedToken = normalizeOverrideToken(token);
            if (normalizedToken.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(normalizedToken);
        }
        return builder.toString();
    }

    private String normalizeOverrideToken(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace("주식회사", "").replace("(주)", "").trim();
        return normalized.replaceAll("[\\s·ㆍ_./()\\-]+", "");
    }

    private String normalizeUrl(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }
}
