package com.algotrader.bot.service.marketdata.provider;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.marketdata.AbstractMarketDataProvider;
import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataHttpClient;
import com.algotrader.bot.service.marketdata.MarketDataProviderCredentialService;
import com.algotrader.bot.service.marketdata.MarketDataProviderDefinition;
import com.algotrader.bot.service.marketdata.MarketDataProviderFetchRequest;
import com.algotrader.bot.service.marketdata.MarketDataResampler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FinnhubMarketDataProvider extends AbstractMarketDataProvider {

    private static final MarketDataProviderDefinition DEFINITION = new MarketDataProviderDefinition(
        "finnhub",
        "Finnhub",
        "Free-key stock candles plus crypto candles sourced from exchange symbols.",
        Set.of(MarketDataAssetType.STOCK, MarketDataAssetType.CRYPTO),
        List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d"),
        true,
        "ALGOTRADING_MARKET_DATA_FINNHUB_API_KEY",
        false,
        true,
        List.of("AAPL", "MSFT", "BTC/USDT"),
        "https://finnhub.io/docs/api/stock-candles",
        "https://finnhub.io/register",
        "Free API key required. Crypto symbols are requested through Finnhub's exchange-style format."
    );

    private final MarketDataResampler marketDataResampler;

    public FinnhubMarketDataProvider(
        MarketDataHttpClient httpClient,
        ObjectMapper objectMapper,
        MarketDataProviderCredentialService marketDataProviderCredentialService,
        MarketDataResampler marketDataResampler
    ) {
        super(httpClient, objectMapper, marketDataProviderCredentialService);
        this.marketDataResampler = marketDataResampler;
    }

    @Override
    public MarketDataProviderDefinition definition() {
        return DEFINITION;
    }

    @Override
    public List<OHLCVData> fetch(MarketDataProviderFetchRequest request) {
        JsonNode root = getJson(
            request.assetType() == MarketDataAssetType.STOCK
                ? "https://finnhub.io/api/v1/stock/candle"
                : "https://finnhub.io/api/v1/crypto/candle",
            Map.of(
                "symbol", providerSymbol(request),
                "resolution", resolution(request),
                "from", String.valueOf(request.start().toEpochSecond(ZoneOffset.UTC)),
                "to", String.valueOf(request.end().toEpochSecond(ZoneOffset.UTC)),
                "token", requiredApiKey()
            )
        );

        if (root.has("error")) {
            throw new IllegalArgumentException("Finnhub request failed: " + root.get("error").asText());
        }
        if ("no_data".equalsIgnoreCase(root.path("s").asText())) {
            return List.of();
        }

        List<OHLCVData> bars = mapArrayPayload(root, toDatasetSymbol(request.assetType(), request.requestedSymbol()), request.start(), request.end());
        if ("4h".equals(request.timeframe().id())) {
            return marketDataResampler.toFourHourBars(bars);
        }
        return bars;
    }

    private List<OHLCVData> mapArrayPayload(JsonNode root, String symbol, LocalDateTime start, LocalDateTime end) {
        JsonNode timestamps = root.path("t");
        JsonNode opens = root.path("o");
        JsonNode highs = root.path("h");
        JsonNode lows = root.path("l");
        JsonNode closes = root.path("c");
        JsonNode volumes = root.path("v");

        List<OHLCVData> bars = new ArrayList<>();
        int size = timestamps.size();
        for (int index = 0; index < size; index++) {
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamps.get(index).asLong()),
                ZoneOffset.UTC
            );
            if (timestamp.isBefore(start) || timestamp.isAfter(end)) {
                continue;
            }
            bars.add(new OHLCVData(
                timestamp,
                symbol,
                new BigDecimal(opens.get(index).asText()),
                new BigDecimal(highs.get(index).asText()),
                new BigDecimal(lows.get(index).asText()),
                new BigDecimal(closes.get(index).asText()),
                new BigDecimal(volumes.get(index).asText())
            ));
        }
        return bars;
    }

    private String resolution(MarketDataProviderFetchRequest request) {
        return switch (request.timeframe().id()) {
            case "1m" -> "1";
            case "5m" -> "5";
            case "15m" -> "15";
            case "30m" -> "30";
            case "1h", "4h" -> "60";
            case "1d" -> "D";
            default -> throw new IllegalArgumentException("Finnhub does not support timeframe " + request.timeframe().id());
        };
    }

    private String providerSymbol(MarketDataProviderFetchRequest request) {
        if (request.assetType() == MarketDataAssetType.STOCK) {
            return normalizeStockSymbol(request.requestedSymbol());
        }
        return "BINANCE:" + normalizeCryptoSymbol(request.requestedSymbol()).replace("/", "");
    }
}
