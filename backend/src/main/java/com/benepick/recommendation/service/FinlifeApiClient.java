package com.benepick.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FinlifeApiClient {

    private final FinlifeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FinlifeApiClient(FinlifeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1000)))
            .build();
    }

    public JsonNode fetchResult(String endpoint, String topFinGrpNo, int pageNo) {
        String authKey = safe(properties.getAuthKey());
        if (authKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FINLIFE_AUTH_KEY is not configured");
        }

        URI uri = buildUri(endpoint, topFinGrpNo, pageNo, authKey);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 3000)))
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
                "Failed to call Finlife API: " + exception.getMessage(),
                exception
            );
        }

        if (response.statusCode() != 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Finlife API returned status " + response.statusCode()
            );
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Finlife API returned empty body");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to parse Finlife API response",
                exception
            );
        }

        JsonNode result = root.path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Finlife API response has no result field");
        }

        String errCode = safe(result.path("err_cd").asText(""));
        if (!errCode.isBlank() && !"000".equals(errCode)) {
            String errMessage = safe(result.path("err_msg").asText(""));
            String detail = errMessage.isBlank() ? "unknown error" : errMessage;
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Finlife API error (" + errCode + "): " + detail
            );
        }

        return result;
    }

    private URI buildUri(String endpoint, String topFinGrpNo, int pageNo, String authKey) {
        String normalizedBase = safe(properties.getBaseUrl());
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        String query = "auth=" + encode(authKey)
            + "&topFinGrpNo=" + encode(safe(topFinGrpNo))
            + "&pageNo=" + pageNo;

        return URI.create(normalizedBase + "/" + endpoint + "?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
