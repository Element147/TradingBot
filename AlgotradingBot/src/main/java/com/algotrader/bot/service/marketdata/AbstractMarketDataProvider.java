package com.algotrader.bot.service.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractMarketDataProvider implements MarketDataProvider {

    protected final MarketDataHttpClient httpClient;
    protected final ObjectMapper objectMapper;
    private final MarketDataProviderCredentialService marketDataProviderCredentialService;

    protected AbstractMarketDataProvider(
        MarketDataHttpClient httpClient,
        ObjectMapper objectMapper,
        MarketDataProviderCredentialService marketDataProviderCredentialService
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.marketDataProviderCredentialService = marketDataProviderCredentialService;
    }

    @Override
    public boolean isConfigured() {
        return marketDataProviderCredentialService.isConfigured(definition());
    }

    protected String requiredApiKey() {
        return marketDataProviderCredentialService.requiredApiKey(definition());
    }

    protected JsonNode getJson(String baseUrl, Map<String, String> queryParameters) {
        MarketDataHttpResponse response = httpClient.get(buildUri(baseUrl, queryParameters), Map.of());
        if (response.statusCode() == 429) {
            throw new MarketDataRetryableException(
                definition().label() + " rate limit reached. Waiting before retrying.",
                retryAtFromHeadersOrDefault(response, 5)
            );
        }
        if (response.statusCode() >= 500) {
            throw new MarketDataRetryableException(
                definition().label() + " is temporarily unavailable (" + response.statusCode() + ").",
                LocalDateTime.now().plusMinutes(5)
            );
        }
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException(
                definition().label() + " rejected the request: " + safeBody(response.body())
            );
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                definition().label() + " returned an unreadable response: " + exception.getMessage(),
                exception
            );
        }
    }

    protected URI buildUri(String baseUrl, Map<String, String> queryParameters) {
        String encodedQuery = queryParameters.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));

        String separator = baseUrl.contains("?") ? "&" : "?";
        return URI.create(encodedQuery.isBlank() ? baseUrl : baseUrl + separator + encodedQuery);
    }

    protected LocalDateTime retryAtFromHeadersOrDefault(MarketDataHttpResponse response, int defaultMinutes) {
        return response.headers().firstValue("Retry-After")
            .map(value -> {
                try {
                    return LocalDateTime.now().plusSeconds(Long.parseLong(value));
                } catch (NumberFormatException exception) {
                    return LocalDateTime.now().plusMinutes(defaultMinutes);
                }
            })
            .orElse(LocalDateTime.now().plusMinutes(defaultMinutes));
    }

    protected Map<String, String> orderedQuery() {
        return new LinkedHashMap<>();
    }

    protected String safeBody(String body) {
        if (body == null || body.isBlank()) {
            return "empty response";
        }
        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    protected String normalizeStockSymbol(String requestedSymbol) {
        return requestedSymbol.trim().toUpperCase(Locale.ROOT);
    }

    protected String normalizeCryptoSymbol(String requestedSymbol) {
        return requestedSymbol.trim().toUpperCase(Locale.ROOT)
            .replace("-", "/")
            .replace("_", "/");
    }

    protected String toDatasetSymbol(MarketDataAssetType assetType, String requestedSymbol) {
        if (assetType == MarketDataAssetType.STOCK) {
            return normalizeStockSymbol(requestedSymbol);
        }
        String normalized = normalizeCryptoSymbol(requestedSymbol);
        if (normalized.contains("/")) {
            return normalized;
        }

        for (String quote : new String[] {"USDT", "USDC", "USD", "EUR", "BTC"}) {
            if (normalized.endsWith(quote) && normalized.length() > quote.length()) {
                return normalized.substring(0, normalized.length() - quote.length()) + "/" + quote;
            }
        }
        return normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
