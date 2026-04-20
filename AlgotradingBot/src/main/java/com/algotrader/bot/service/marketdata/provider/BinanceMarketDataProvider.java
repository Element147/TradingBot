package com.algotrader.bot.service.marketdata.provider;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.marketdata.AbstractMarketDataProvider;
import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataHttpClient;
import com.algotrader.bot.service.marketdata.MarketDataProviderCredentialService;
import com.algotrader.bot.service.marketdata.MarketDataProviderDefinition;
import com.algotrader.bot.service.marketdata.MarketDataProviderFetchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BinanceMarketDataProvider extends AbstractMarketDataProvider {

    private static final MarketDataProviderDefinition DEFINITION = new MarketDataProviderDefinition(
        "binance",
        "Binance",
        "Public spot klines for major crypto pairs with no API key required.",
        Set.of(MarketDataAssetType.CRYPTO),
        List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d"),
        false,
        null,
        false,
        false,
        List.of("BTC/USDT", "ETH/USDT", "SOL/USDT"),
        "https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints",
        "https://www.binance.com/",
        "No API key is needed for historical spot klines."
    );

    public BinanceMarketDataProvider(
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
        if (request.assetType() != MarketDataAssetType.CRYPTO) {
            throw new IllegalArgumentException("Binance only supports crypto imports.");
        }

        JsonNode root = getJson(
            "https://api.binance.com/api/v3/klines",
            Map.of(
                "symbol", providerSymbol(request.requestedSymbol()),
                "interval", request.timeframe().id(),
                "startTime", String.valueOf(request.start().toInstant(ZoneOffset.UTC).toEpochMilli()),
                "endTime", String.valueOf(request.end().toInstant(ZoneOffset.UTC).toEpochMilli()),
                "limit", "1000"
            )
        );

        if (root.isObject() && root.has("msg")) {
            throw new IllegalArgumentException("Binance request failed: " + root.get("msg").asText());
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("Binance returned an unexpected payload.");
        }

        String symbol = toDatasetSymbol(request.assetType(), request.requestedSymbol());
        return mapKlines(root, symbol, request.start(), request.end());
    }

    private List<OHLCVData> mapKlines(JsonNode root, String symbol, LocalDateTime start, LocalDateTime end) {
        java.util.ArrayList<OHLCVData> bars = new java.util.ArrayList<>();
        for (JsonNode node : root) {
            if (!node.isArray() || node.size() < 6) {
                continue;
            }
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(node.get(0).asLong()),
                ZoneOffset.UTC
            );
            if (timestamp.isBefore(start) || timestamp.isAfter(end)) {
                continue;
            }
            bars.add(new OHLCVData(
                timestamp,
                symbol,
                new BigDecimal(node.get(1).asText()),
                new BigDecimal(node.get(2).asText()),
                new BigDecimal(node.get(3).asText()),
                new BigDecimal(node.get(4).asText()),
                new BigDecimal(node.get(5).asText())
            ));
        }
        return bars;
    }

    private String providerSymbol(String requestedSymbol) {
        return normalizeCryptoSymbol(requestedSymbol).replace("/", "");
    }
}
