package com.algotrader.bot.service;

import com.algotrader.bot.controller.ExchangeConnectionStatusResponse;
import com.algotrader.bot.controller.ExchangeConnectionTestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ExchangeIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeIntegrationService.class);

    private static final String DEFAULT_EXCHANGE = "binance";
    private static final String BINANCE_MAINNET_URL = "https://api.binance.com";
    private static final String BINANCE_TESTNET_URL = "https://testnet.binance.vision";
    private static final String BINANCE_USED_WEIGHT_HEADER = "X-MBX-USED-WEIGHT-1M";

    private final OperatorAuditService operatorAuditService;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final ConcurrentMap<String, ExchangeConnectionStatusResponse> lastStatusByActor =
        new ConcurrentHashMap<>();

    public ExchangeIntegrationService(OperatorAuditService operatorAuditService) {
        this.operatorAuditService = operatorAuditService;
    }

    public ExchangeConnectionStatusResponse getConnectionStatus() {
        return lastStatusByActor.getOrDefault(resolveActor(), defaultStatus());
    }

    public String getLiveAccountReadUnavailableReason() {
        ExchangeConnectionStatusResponse status = getConnectionStatus();
        if (status.connected()) {
            return "Live account reads are unavailable on this backend. Exchange connectivity is verified, but /api/account/* is not wired to live exchange balances, positions, or trade history.";
        }
        return "Live account reads are unavailable on this backend. The exchange integration currently supports connectivity testing only and will not fall back to paper-trading data.";
    }

    public ExchangeConnectionStatusResponse testConnection(ExchangeConnectionTestRequest request) {
        String exchange = normalizeExchange(request);

        if (!DEFAULT_EXCHANGE.equals(exchange)) {
            ExchangeConnectionStatusResponse unsupported = new ExchangeConnectionStatusResponse(
                false,
                exchange,
                LocalDateTime.now(),
                "n/a",
                "Only Binance connectivity test is currently supported."
            );
            rememberStatus(unsupported);
            operatorAuditService.recordFailure(
                "EXCHANGE_CONNECTION_TEST",
                "test",
                "EXCHANGE",
                exchange,
                unsupported.error()
            );
            return unsupported;
        }

        String apiKey = trimToNull(request == null ? null : request.apiKey());
        String apiSecret = trimToNull(request == null ? null : request.apiSecret());
        if (apiKey == null) {
            apiKey = trimToNull(System.getenv("BINANCE_API_KEY"));
        }
        if (apiSecret == null) {
            apiSecret = trimToNull(System.getenv("BINANCE_API_SECRET"));
        }

        if (apiKey == null || apiSecret == null) {
            ExchangeConnectionStatusResponse missingCredentials = new ExchangeConnectionStatusResponse(
                false,
                exchange,
                LocalDateTime.now(),
                "n/a",
                "Missing API credentials. Provide API key/secret in request or BINANCE_API_KEY/BINANCE_API_SECRET."
            );
            rememberStatus(missingCredentials);
            operatorAuditService.recordFailure(
                "EXCHANGE_CONNECTION_TEST",
                "test",
                "EXCHANGE",
                exchange,
                missingCredentials.error()
            );
            return missingCredentials;
        }

        boolean testnet = request != null && Boolean.TRUE.equals(request.testnet());
        ExchangeConnectionStatusResponse status = performBinanceConnectivityTest(apiKey, apiSecret, testnet);
        rememberStatus(status);
        if (status.connected()) {
            operatorAuditService.recordSuccess(
                "EXCHANGE_CONNECTION_TEST",
                testnet ? "test" : "live",
                "EXCHANGE",
                DEFAULT_EXCHANGE,
                status.rateLimitUsage()
            );
        } else {
            operatorAuditService.recordFailure(
                "EXCHANGE_CONNECTION_TEST",
                testnet ? "test" : "live",
                "EXCHANGE",
                DEFAULT_EXCHANGE,
                status.error()
            );
        }
        return status;
    }

    private ExchangeConnectionStatusResponse performBinanceConnectivityTest(
        String apiKey,
        String apiSecret,
        boolean testnet
    ) {
        String baseUrl = testnet ? BINANCE_TESTNET_URL : BINANCE_MAINNET_URL;
        long timestamp = System.currentTimeMillis();
        String query = "timestamp=" + timestamp + "&recvWindow=5000";
        String signature = sign(query, apiSecret);
        String targetUrl = baseUrl + "/api/v3/account?" + query + "&signature=" + signature;

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(8))
                .header("X-MBX-APIKEY", apiKey)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String usedWeight = Optional.ofNullable(response.headers().firstValue(BINANCE_USED_WEIGHT_HEADER).orElse(null))
                .orElse("n/a");

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ExchangeConnectionStatusResponse(
                    true,
                    DEFAULT_EXCHANGE,
                    LocalDateTime.now(),
                    "used-weight-1m=" + usedWeight,
                    null
                );
            }

            return new ExchangeConnectionStatusResponse(
                false,
                DEFAULT_EXCHANGE,
                LocalDateTime.now(),
                "used-weight-1m=" + usedWeight,
                "Exchange rejected credentials or request (HTTP " + response.statusCode() + ")."
            );
        } catch (Exception ex) {
            logger.warn("Binance connection test failed: {}", ex.getMessage());
            return new ExchangeConnectionStatusResponse(
                false,
                DEFAULT_EXCHANGE,
                LocalDateTime.now(),
                "n/a",
                "Connection test failed: " + ex.getClass().getSimpleName()
            );
        }
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign Binance request", ex);
        }
    }

    private String normalizeExchange(ExchangeConnectionTestRequest request) {
        if (request == null || request.exchange() == null || request.exchange().isBlank()) {
            return DEFAULT_EXCHANGE;
        }
        return request.exchange().trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ExchangeConnectionStatusResponse defaultStatus() {
        return new ExchangeConnectionStatusResponse(
            false,
            DEFAULT_EXCHANGE,
            LocalDateTime.now(),
            "n/a",
            "Credentials not tested yet."
        );
    }

    private void rememberStatus(ExchangeConnectionStatusResponse status) {
        lastStatusByActor.put(resolveActor(), status);
    }

    private String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return "system";
        }
        return name;
    }
}
