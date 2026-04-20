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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AlphaVantageMarketDataProvider extends AbstractMarketDataProvider {

    private static final MarketDataProviderDefinition DEFINITION = new MarketDataProviderDefinition(
        "alphavantage",
        "Alpha Vantage",
        "Free-key stock history including intraday and daily adjusted series.",
        Set.of(MarketDataAssetType.STOCK),
        List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d"),
        true,
        "ALGOTRADING_MARKET_DATA_ALPHAVANTAGE_API_KEY",
        true,
        true,
        List.of("AAPL", "MSFT", "SPY"),
        "https://www.alphavantage.co/documentation/",
        "https://www.alphavantage.co/support/#api-key",
        "Free API key required. Intraday requests are fetched month-by-month; 4h bars are resampled from 60min data."
    );

    private final MarketDataResampler marketDataResampler;

    public AlphaVantageMarketDataProvider(
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
    public LocalDateTime resolveChunkEnd(MarketDataProviderFetchRequest request) {
        if (!request.timeframe().isIntraday()) {
            return request.end();
        }
        LocalDate monthEnd = request.start().toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
        LocalDateTime monthEndDateTime = monthEnd.atTime(23, 59, 59);
        return monthEndDateTime.isBefore(request.end()) ? monthEndDateTime : request.end();
    }

    @Override
    public List<OHLCVData> fetch(MarketDataProviderFetchRequest request) {
        if (request.assetType() != MarketDataAssetType.STOCK) {
            throw new IllegalArgumentException("Alpha Vantage downloader only supports stock imports.");
        }

        JsonNode root = request.timeframe().isIntraday()
            ? fetchIntraday(request)
            : fetchDaily(request);

        if (root.has("Error Message")) {
            throw new IllegalArgumentException("Alpha Vantage request failed: " + root.get("Error Message").asText());
        }
        if (root.has("Information")) {
            throw new com.algotrader.bot.service.marketdata.MarketDataRetryableException(
                root.get("Information").asText(),
                LocalDateTime.now().plusHours(1)
            );
        }
        if (root.has("Note")) {
            throw new com.algotrader.bot.service.marketdata.MarketDataRetryableException(
                root.get("Note").asText(),
                LocalDateTime.now().plusMinutes(15)
            );
        }

        JsonNode series = locateSeries(root);
        String symbol = normalizeStockSymbol(request.requestedSymbol());
        List<OHLCVData> bars = parseSeries(series, symbol, request.start(), request.end(), request.timeframe().isIntraday());
        if ("4h".equals(request.timeframe().id())) {
            return marketDataResampler.toFourHourBars(bars);
        }
        return bars;
    }

    private JsonNode fetchIntraday(MarketDataProviderFetchRequest request) {
        String interval = "4h".equals(request.timeframe().id()) ? "60min" : interval(request.timeframe().id());
        return getJson(
            "https://www.alphavantage.co/query",
            Map.of(
                "function", "TIME_SERIES_INTRADAY",
                "symbol", normalizeStockSymbol(request.requestedSymbol()),
                "interval", interval,
                "month", request.start().toLocalDate().withDayOfMonth(1).toString().substring(0, 7),
                "outputsize", "full",
                "datatype", "json",
                "adjusted", String.valueOf(request.adjusted()),
                "extended_hours", String.valueOf(!request.regularSessionOnly()),
                "apikey", requiredApiKey()
            )
        );
    }

    private JsonNode fetchDaily(MarketDataProviderFetchRequest request) {
        return getJson(
            "https://www.alphavantage.co/query",
            Map.of(
                "function", "TIME_SERIES_DAILY",
                "symbol", normalizeStockSymbol(request.requestedSymbol()),
                "outputsize", "full",
                "datatype", "json",
                "adjusted", String.valueOf(request.adjusted()),
                "apikey", requiredApiKey()
            )
        );
    }

    private JsonNode locateSeries(JsonNode root) {
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (fieldName.startsWith("Time Series")) {
                return root.get(fieldName);
            }
        }
        throw new IllegalArgumentException("Alpha Vantage did not return a time series payload.");
    }

    private List<OHLCVData> parseSeries(
        JsonNode series,
        String symbol,
        LocalDateTime start,
        LocalDateTime end,
        boolean intraday
    ) {
        List<OHLCVData> bars = new ArrayList<>();
        Iterator<String> fieldNames = series.fieldNames();
        while (fieldNames.hasNext()) {
            String timestampText = fieldNames.next();
            LocalDateTime timestamp = intraday
                ? LocalDateTime.parse(timestampText.replace(' ', 'T'))
                : LocalDate.parse(timestampText).atStartOfDay();
            if (timestamp.isBefore(start) || timestamp.isAfter(end)) {
                continue;
            }
            JsonNode row = series.get(timestampText);
            bars.add(new OHLCVData(
                timestamp,
                symbol,
                new BigDecimal(row.get("1. open").asText()),
                new BigDecimal(row.get("2. high").asText()),
                new BigDecimal(row.get("3. low").asText()),
                new BigDecimal(row.get("4. close").asText()),
                new BigDecimal(row.get("5. volume").asText())
            ));
        }
        return bars.stream().sorted(java.util.Comparator.comparing(OHLCVData::getTimestamp)).toList();
    }

    private String interval(String timeframeId) {
        return switch (timeframeId) {
            case "1m" -> "1min";
            case "5m" -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "1h" -> "60min";
            default -> throw new IllegalArgumentException("Alpha Vantage does not support timeframe " + timeframeId);
        };
    }
}
