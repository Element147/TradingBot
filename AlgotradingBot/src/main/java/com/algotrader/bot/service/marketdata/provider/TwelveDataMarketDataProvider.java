package com.algotrader.bot.service.marketdata.provider;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.marketdata.AbstractMarketDataProvider;
import com.algotrader.bot.service.marketdata.MarketDataHttpClient;
import com.algotrader.bot.service.marketdata.MarketDataProviderCredentialService;
import com.algotrader.bot.service.marketdata.MarketDataProviderDefinition;
import com.algotrader.bot.service.marketdata.MarketDataProviderFetchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.algotrader.bot.service.marketdata.MarketDataAssetType.CRYPTO;
import static com.algotrader.bot.service.marketdata.MarketDataAssetType.STOCK;

@Component
public class TwelveDataMarketDataProvider extends AbstractMarketDataProvider {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final MarketDataProviderDefinition DEFINITION = new MarketDataProviderDefinition(
        "twelvedata",
        "Twelve Data",
        "Unified time series API for stocks and crypto with free-key access.",
        Set.of(STOCK, CRYPTO),
        List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d"),
        true,
        "ALGOTRADING_MARKET_DATA_TWELVEDATA_API_KEY",
        false,
        true,
        List.of("AAPL", "MSFT", "BTC/USD"),
        "https://twelvedata.com/docs#time-series",
        "https://twelvedata.com/pricing",
        "Free API key required. Suitable for both stock and crypto research imports."
    );

    public TwelveDataMarketDataProvider(
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
    public List<OHLCVData> fetch(MarketDataProviderFetchRequest request) {
        JsonNode root = getJson(
            "https://api.twelvedata.com/time_series",
            Map.of(
                "symbol", request.assetType() == STOCK ? normalizeStockSymbol(request.requestedSymbol()) : normalizeCryptoSymbol(request.requestedSymbol()),
                "interval", interval(request),
                "start_date", request.start().format(FORMATTER),
                "end_date", request.end().format(FORMATTER),
                "timezone", "UTC",
                "order", "ASC",
                "outputsize", "5000",
                "apikey", requiredApiKey()
            )
        );

        if ("error".equalsIgnoreCase(root.path("status").asText())) {
            String message = root.path("message").asText("Twelve Data request failed.");
            if (message.toLowerCase().contains("rate limit")) {
                throw new com.algotrader.bot.service.marketdata.MarketDataRetryableException(
                    message,
                    LocalDateTime.now().plusMinutes(5)
                );
            }
            throw new IllegalArgumentException(message);
        }

        JsonNode values = root.path("values");
        if (!values.isArray()) {
            return List.of();
        }

        String symbol = toDatasetSymbol(request.assetType(), request.requestedSymbol());
        List<OHLCVData> bars = new ArrayList<>();
        for (JsonNode value : values) {
            LocalDateTime timestamp = LocalDateTime.parse(value.path("datetime").asText(), FORMATTER);
            if (timestamp.isBefore(request.start()) || timestamp.isAfter(request.end())) {
                continue;
            }
            bars.add(new OHLCVData(
                timestamp,
                symbol,
                new BigDecimal(value.path("open").asText()),
                new BigDecimal(value.path("high").asText()),
                new BigDecimal(value.path("low").asText()),
                new BigDecimal(value.path("close").asText()),
                parseVolume(value.path("volume").asText("0"))
            ));
        }
        return bars;
    }

    private String interval(MarketDataProviderFetchRequest request) {
        return switch (request.timeframe().id()) {
            case "1m" -> "1min";
            case "5m" -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "1h" -> "1h";
            case "4h" -> "4h";
            case "1d" -> "1day";
            default -> throw new IllegalArgumentException("Twelve Data does not support timeframe " + request.timeframe().id());
        };
    }

    private BigDecimal parseVolume(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || "null".equalsIgnoreCase(rawValue)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rawValue);
    }
}
