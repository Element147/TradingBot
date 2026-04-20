package com.algotrader.bot.service.marketdata.provider;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.marketdata.AbstractMarketDataProvider;
import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataHttpClient;
import com.algotrader.bot.service.marketdata.MarketDataProviderCredentialService;
import com.algotrader.bot.service.marketdata.MarketDataProviderDefinition;
import com.algotrader.bot.service.marketdata.MarketDataProviderFetchRequest;
import com.algotrader.bot.service.marketdata.MarketDataRetryableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KrakenMarketDataProvider extends AbstractMarketDataProvider {

    private static final int MAX_RECENT_CANDLES = 720;
    private static final Pattern THROTTLED_UNIX_TIMESTAMP = Pattern.compile("(\\d{10})");
    private static final MarketDataProviderDefinition DEFINITION = new MarketDataProviderDefinition(
        "kraken",
        "Kraken",
        "Public OHLC market data for major crypto pairs with no API key required. Imports are limited to Kraken's rolling 720-candle window.",
        Set.of(MarketDataAssetType.CRYPTO),
        List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d"),
        false,
        null,
        false,
        false,
        List.of("BTC/USD", "ETH/USD", "SOL/USD"),
        "https://docs.kraken.com/api/docs/rest-api/get-ohlc-data/",
        "https://www.kraken.com/",
        "No API key is needed for public OHLC queries. Kraken only exposes the latest 720 candles for each timeframe."
    );

    public KrakenMarketDataProvider(
        MarketDataHttpClient httpClient,
        ObjectMapper objectMapper,
        MarketDataProviderCredentialService marketDataProviderCredentialService
    ) {
        super(httpClient, objectMapper, marketDataProviderCredentialService);
    }

    @Override
    public MarketDataProviderDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void validateImportRequest(MarketDataProviderFetchRequest request) {
        LocalDateTime earliestRetainedCandle = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
            .minus(request.timeframe().step().multipliedBy(MAX_RECENT_CANDLES - 1L));
        if (!request.start().isBefore(earliestRetainedCandle)) {
            return;
        }

        throw new IllegalArgumentException(
            "Kraken only exposes the most recent " + MAX_RECENT_CANDLES + " candles per timeframe. "
                + "Earliest retrievable " + request.timeframe().id() + " candle is about "
                + formatUtc(earliestRetainedCandle) + ", but the request starts at "
                + formatUtc(request.start()) + ". Use Binance or a CSV upload for older history."
        );
    }

    @Override
    public LocalDateTime resolveChunkEnd(MarketDataProviderFetchRequest request) {
        LocalDateTime cappedEnd = request.start()
            .plus(request.timeframe().step().multipliedBy(MAX_RECENT_CANDLES - 1L));
        return cappedEnd.isBefore(request.end()) ? cappedEnd : request.end();
    }

    @Override
    public List<OHLCVData> fetch(MarketDataProviderFetchRequest request) {
        validateImportRequest(request);
        if (request.assetType() != MarketDataAssetType.CRYPTO) {
            throw new IllegalArgumentException("Kraken only supports crypto imports.");
        }

        JsonNode root = getJson(
            "https://api.kraken.com/0/public/OHLC",
            Map.of(
                "pair", providerPair(request.requestedSymbol()),
                "interval", intervalMinutes(request),
                "since", String.valueOf(request.start().toEpochSecond(ZoneOffset.UTC))
            )
        );

        JsonNode errors = root.path("error");
        if (errors.isArray() && !errors.isEmpty()) {
            throw translateKrakenError(errors.get(0).asText());
        }

        JsonNode result = root.path("result");
        if (!result.isObject()) {
            throw new IllegalArgumentException("Kraken returned an unexpected payload.");
        }

        Iterator<String> fieldNames = result.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if ("last".equals(fieldName)) {
                continue;
            }
            JsonNode rows = result.get(fieldName);
            if (!rows.isArray()) {
                continue;
            }

            String symbol = toDatasetSymbol(request.assetType(), request.requestedSymbol());
            List<OHLCVData> bars = new ArrayList<>();
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 7) {
                    continue;
                }
                LocalDateTime timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(row.get(0).asLong()),
                    ZoneOffset.UTC
                );
                if (timestamp.isBefore(request.start()) || timestamp.isAfter(request.end())) {
                    continue;
                }
                bars.add(new OHLCVData(
                    timestamp,
                    symbol,
                    new BigDecimal(row.get(1).asText()),
                    new BigDecimal(row.get(2).asText()),
                    new BigDecimal(row.get(3).asText()),
                    new BigDecimal(row.get(4).asText()),
                    new BigDecimal(row.get(6).asText())
                ));
            }
            return bars;
        }

        return List.of();
    }

    private String intervalMinutes(MarketDataProviderFetchRequest request) {
        return switch (request.timeframe().id()) {
            case "1m" -> "1";
            case "5m" -> "5";
            case "15m" -> "15";
            case "30m" -> "30";
            case "1h" -> "60";
            case "4h" -> "240";
            case "1d" -> "1440";
            default -> throw new IllegalArgumentException("Kraken does not support timeframe " + request.timeframe().id());
        };
    }

    private RuntimeException translateKrakenError(String rawError) {
        String message = "Kraken request failed: " + rawError;
        String normalized = rawError == null ? "" : rawError.toLowerCase(Locale.ROOT);
        if (normalized.contains("too many requests")
            || normalized.contains("rate limit")
            || normalized.contains("throttled")) {
            return new MarketDataRetryableException(message, retryAtFromKrakenError(rawError));
        }
        return new IllegalArgumentException(message);
    }

    private LocalDateTime retryAtFromKrakenError(String rawError) {
        if (rawError != null) {
            Matcher matcher = THROTTLED_UNIX_TIMESTAMP.matcher(rawError);
            if (matcher.find()) {
                return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(matcher.group(1))),
                    ZoneOffset.UTC
                );
            }
        }
        return LocalDateTime.now().plusMinutes(1);
    }

    private String formatUtc(LocalDateTime timestamp) {
        return timestamp.atOffset(ZoneOffset.UTC).toLocalDateTime() + " UTC";
    }

    private String providerPair(String requestedSymbol) {
        String normalized = normalizeCryptoSymbol(requestedSymbol);
        if (!normalized.contains("/")) {
            return normalized.replace("BTC", "XBT");
        }
        String[] parts = normalized.split("/");
        String base = "BTC".equals(parts[0]) ? "XBT" : parts[0];
        return base + parts[1];
    }
}
