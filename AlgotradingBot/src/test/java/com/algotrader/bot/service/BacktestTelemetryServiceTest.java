package com.algotrader.bot.service;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.backtest.strategy.BacktestIndicatorCalculator;
import com.algotrader.bot.controller.BacktestActionMarkerResponse;
import com.algotrader.bot.controller.BacktestSymbolTelemetryResponse;
import com.algotrader.bot.entity.BacktestEquityPoint;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.service.marketdata.MarketDataCandleProvenance;
import com.algotrader.bot.service.marketdata.MarketDataQueryMode;
import com.algotrader.bot.service.marketdata.MarketDataQueryResult;
import com.algotrader.bot.service.marketdata.MarketDataQueriedCandle;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BacktestTelemetryServiceTest {

    private MarketDataQueryService marketDataQueryService;
    private BacktestTelemetryService backtestTelemetryService;

    @BeforeEach
    void setUp() {
        marketDataQueryService = mock(MarketDataQueryService.class);
        backtestTelemetryService = new BacktestTelemetryService(
            marketDataQueryService,
            new BacktestIndicatorCalculator(),
            new BackendOperationMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void buildTelemetry_generatesMarkersExposureAndIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("BTC/USDT", "1h", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(1L, "SMA_CROSSOVER", "BTC/USDT", candles);
        result.addTradeSeriesItem(trade(
            "BTC/USDT",
            PositionSide.LONG,
            candles.get(210).timestamp(),
            candles.get(225).timestamp(),
            new BigDecimal("310"),
            new BigDecimal("325")
        ));

        when(marketDataQueryService.queryCandlesForDataset(
            1L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("BTC/USDT"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        BacktestSymbolTelemetryResponse symbolTelemetry = telemetry.get(0);
        assertEquals("BTC/USDT", symbolTelemetry.symbol());
        assertEquals(candles.size(), symbolTelemetry.points().size());
        assertFalse(symbolTelemetry.indicators().isEmpty());
        assertFalse(symbolTelemetry.provenance().isEmpty());
        assertTrue(symbolTelemetry.indicators().stream().anyMatch(series -> "sma_fast_10".equals(series.key())));
        assertTrue(symbolTelemetry.actions().stream().map(BacktestActionMarkerResponse::action).toList().contains("BUY"));
        assertTrue(symbolTelemetry.actions().stream().map(BacktestActionMarkerResponse::action).toList().contains("SELL"));
        assertTrue(symbolTelemetry.points().stream().anyMatch(point -> point.exposurePct().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(symbolTelemetry.points().stream().anyMatch(point -> "TREND_UP".equals(point.regime())));
        assertTrue(symbolTelemetry.points().stream().allMatch(point -> point.segmentId() != null));
    }

    @Test
    void buildTelemetry_forDatasetUniversePrefersTradedSymbols() {
        List<MarketDataQueriedCandle> candles = createUniverseCandles();
        BacktestResult result = baseResult(2L, "DUAL_MOMENTUM_ROTATION", "DATASET_UNIVERSE", candles);
        result.addTradeSeriesItem(trade(
            "ETH/USDT",
            PositionSide.LONG,
            LocalDateTime.parse("2025-01-10T00:00:00"),
            LocalDateTime.parse("2025-01-11T00:00:00"),
            new BigDecimal("125"),
            new BigDecimal("130")
        ));

        when(marketDataQueryService.queryCandlesForDataset(
            2L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("ETH/USDT"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(
            candles.stream().filter(candle -> "ETH/USDT".equals(candle.symbol())).toList(),
            List.of(),
            "1h",
            MarketDataQueryMode.BEST_AVAILABLE
        ));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertFalse(telemetry.isEmpty());
        assertTrue(telemetry.stream().anyMatch(series -> "ETH/USDT".equals(series.symbol())));
    }

    @Test
    void buildTelemetry_bollingerBandsIncludesTrendAndAtrIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("BTC/USDT", "1h", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(4L, "BOLLINGER_BANDS", "BTC/USDT", candles);

        when(marketDataQueryService.queryCandlesForDataset(
            4L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("BTC/USDT"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ema_50".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "atr_14".equals(series.key())));
    }

    @Test
    void buildTelemetry_donchianIncludesManagedAllocationIndicator() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("BTC/USDT", "1h", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(6L, "VOLATILITY_MANAGED_DONCHIAN_BREAKOUT", "BTC/USDT", candles);

        when(marketDataQueryService.queryCandlesForDataset(
            6L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("BTC/USDT"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "volatility_allocation_20".equals(series.key())));
    }

    @Test
    void buildTelemetry_openingRangeBreakoutIncludesSessionIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("SPY", "15m", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(7L, "OPENING_RANGE_VWAP_BREAKOUT", "SPY", candles);
        result.setTimeframe("15m");

        when(marketDataQueryService.queryCandlesForDataset(
            7L,
            "15m",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("SPY"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "15m", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "session_vwap".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "opening_range_high".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "volume_ratio_20".equals(series.key())));
    }

    @Test
    void buildTelemetry_vwapPullbackContinuationIncludesResumeIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("SPY", "15m", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(8L, "VWAP_PULLBACK_CONTINUATION", "SPY", candles);
        result.setTimeframe("15m");

        when(marketDataQueryService.queryCandlesForDataset(
            8L,
            "15m",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("SPY"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "15m", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "session_vwap".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ema_5".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "rsi_5".equals(series.key())));
    }

    @Test
    void buildTelemetry_exhaustionReversalFadeIncludesExhaustionIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("SPY", "15m", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(9L, "EXHAUSTION_REVERSAL_FADE", "SPY", candles);
        result.setTimeframe("15m");

        when(marketDataQueryService.queryCandlesForDataset(
            9L,
            "15m",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("SPY"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "15m", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "bb_lower_20".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "adx_14".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "volume_ratio_20".equals(series.key())));
    }

    @Test
    void buildTelemetry_multiTimeframePullbackIncludesTrendContextIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("SPY", "1h", 260, new BigDecimal("100"));
        BacktestResult result = baseResult(10L, "MULTI_TIMEFRAME_EMA_ADX_PULLBACK", "SPY", candles);

        when(marketDataQueryService.queryCandlesForDataset(
            10L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("SPY"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ema_8".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ema_21".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ema_200".equals(series.key())));
    }

    @Test
    void buildTelemetry_squeezeBreakoutIncludesCompressionIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("SPY", "1h", 260, new BigDecimal("100"));
        BacktestResult result = baseResult(11L, "SQUEEZE_BREAKOUT_REGIME_CONFIRMATION", "SPY", candles);

        when(marketDataQueryService.queryCandlesForDataset(
            11L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("SPY"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "breakout_high_20".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "bb_upper_20".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "adx_14".equals(series.key())));
    }

    @Test
    void buildTelemetry_relativeStrengthRotationIncludesRankingAndTimingIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("SPY", "1h", 260, new BigDecimal("100"));
        BacktestResult result = baseResult(12L, "RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER", "SPY", candles);

        when(marketDataQueryService.queryCandlesForDataset(
            12L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("SPY"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "return_63".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "breakout_high_5".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "rsi_5".equals(series.key())));
    }

    @Test
    void buildTelemetry_ichimokuTrendIncludesCloudIndicators() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("BTC/USDT", "1h", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(5L, "ICHIMOKU_TREND", "BTC/USDT", candles);

        when(marketDataQueryService.queryCandlesForDataset(
            5L,
            "1h",
            result.getStartDate(),
            result.getEndDate(),
            Set.of("BTC/USDT"),
            MarketDataQueryMode.BEST_AVAILABLE
        )).thenReturn(new MarketDataQueryResult(candles, List.of(), "1h", MarketDataQueryMode.BEST_AVAILABLE));

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertEquals(1, telemetry.size());
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ichimoku_conversion_9".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ichimoku_cloud_a_26".equals(series.key())));
        assertTrue(telemetry.get(0).indicators().stream().anyMatch(series -> "ichimoku_cloud_b_52".equals(series.key())));
    }

    @Test
    void buildTelemetry_skipsIncompleteRuns() {
        List<MarketDataQueriedCandle> candles = createRisingCandles("BTC/USDT", "1h", 240, new BigDecimal("100"));
        BacktestResult result = baseResult(3L, "SMA_CROSSOVER", "BTC/USDT", candles);
        result.setExecutionStatus(BacktestResult.ExecutionStatus.RUNNING);

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(result);

        assertTrue(telemetry.isEmpty());
        verifyNoInteractions(marketDataQueryService);
    }

    private BacktestResult baseResult(Long datasetId,
                                      String strategyId,
                                      String symbol,
                                      List<MarketDataQueriedCandle> candles) {
        BacktestResult result = new BacktestResult();
        result.setDatasetId(datasetId);
        result.setStrategyId(strategyId);
        result.setSymbol(symbol);
        result.setTimeframe("1h");
        result.setInitialBalance(new BigDecimal("1000"));
        result.setStartDate(candles.get(0).timestamp());
        result.setEndDate(candles.get(candles.size() - 1).timestamp());
        result.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);

        for (int index = 0; index < candles.size(); index++) {
            BacktestEquityPoint point = new BacktestEquityPoint();
            point.setPointTimestamp(candles.get(index).timestamp());
            point.setEquity(new BigDecimal("1000").add(BigDecimal.valueOf(index)));
            point.setDrawdownPct(BigDecimal.ZERO);
            result.addEquityPoint(point);
        }

        return result;
    }

    private BacktestTradeSeriesItem trade(String symbol,
                                          PositionSide side,
                                          LocalDateTime entryTime,
                                          LocalDateTime exitTime,
                                          BigDecimal entryPrice,
                                          BigDecimal exitPrice) {
        BacktestTradeSeriesItem trade = new BacktestTradeSeriesItem();
        trade.setSymbol(symbol);
        trade.setPositionSide(side);
        trade.setEntryTime(entryTime);
        trade.setExitTime(exitTime);
        trade.setEntryPrice(entryPrice);
        trade.setExitPrice(exitPrice);
        trade.setQuantity(new BigDecimal("1"));
        trade.setEntryValue(entryPrice);
        trade.setExitValue(exitPrice);
        trade.setReturnPct(exitPrice.subtract(entryPrice).divide(entryPrice, 4, RoundingMode.HALF_UP));
        return trade;
    }

    private List<MarketDataQueriedCandle> createRisingCandles(String symbol,
                                                              String timeframe,
                                                              int count,
                                                              BigDecimal startPrice) {
        List<MarketDataQueriedCandle> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < count; index++) {
            BigDecimal close = startPrice.add(BigDecimal.valueOf(index));
            candles.add(candle(start.plusHours(index), symbol, timeframe, close, 10L + index, 20L + index));
        }

        return candles;
    }

    private List<MarketDataQueriedCandle> createUniverseCandles() {
        List<MarketDataQueriedCandle> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 240; index++) {
            candles.add(candle(start.plusHours(index), "BTC/USDT", "1h", BigDecimal.valueOf(100 + index * 1.1), 100L, 200L));
            candles.add(candle(start.plusHours(index), "ETH/USDT", "1h", BigDecimal.valueOf(120 + index * 0.8), 101L, 201L));
        }

        return candles;
    }

    private MarketDataQueriedCandle candle(LocalDateTime timestamp,
                                           String symbol,
                                           String timeframe,
                                           BigDecimal close,
                                           Long segmentId,
                                           Long seriesId) {
        return new MarketDataQueriedCandle(
            timestamp,
            symbol,
            close,
            close.add(BigDecimal.ONE),
            close.subtract(BigDecimal.ONE),
            close,
            BigDecimal.valueOf(1000),
            new MarketDataCandleProvenance(
                99L,
                12L,
                segmentId,
                seriesId,
                "stub",
                "BINANCE",
                symbol,
                timeframe,
                "EXACT_RAW",
                "UPLOAD",
                timestamp.minusHours(4),
                timestamp.plusHours(4)
            )
        );
    }
}
