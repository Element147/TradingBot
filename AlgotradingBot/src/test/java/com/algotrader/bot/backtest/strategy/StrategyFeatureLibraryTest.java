package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyFeatureLibraryTest {

    @Test
    void trendFilter_usesCurrentAndPreviousBarsOnly() {
        RecordingIndicatorCalculator indicatorCalculator = new RecordingIndicatorCalculator();
        StrategyFeatureLibrary featureLibrary = new StrategyFeatureLibrary(indicatorCalculator);

        StrategyFeatureLibrary.TrendFilterSnapshot snapshot = featureLibrary.trendFilter(
            createFlatCandles(240, bd("112"), bd("1000")),
            205,
            new StrategyFeatureLibrary.TrendFilterSpec(200, 50, 20, bd("0.97"))
        );

        assertTrue(snapshot.longTrendUp());
        assertTrue(snapshot.mediumTrendUp());
        assertTrue(snapshot.fastTrendUp());
        assertTrue(snapshot.aboveFloor());
        assertTrue(snapshot.alignedBullish());
        assertEquals(List.of("200:205", "50:205", "20:205", "200:204", "50:204", "20:204"), indicatorCalculator.emaRequests());
    }

    @Test
    void volumeConfirmation_usesAverageVolumeRatio() {
        StrategyFeatureLibrary featureLibrary = new StrategyFeatureLibrary(new BacktestIndicatorCalculator());

        StrategyFeatureLibrary.VolumeConfirmationSnapshot snapshot = featureLibrary.volumeConfirmation(
            createFlatCandles(6, bd("100"), List.of(
                bd("100"),
                bd("100"),
                bd("100"),
                bd("100"),
                bd("100"),
                bd("300")
            )),
            5,
            new StrategyFeatureLibrary.VolumeConfirmationSpec(4, bd("1.50"))
        );

        assertEquals(0, bd("2.0").compareTo(snapshot.ratio()));
        assertTrue(snapshot.confirmed());
    }

    @Test
    void volatilityFilter_reportsManagedAllocationAtrCapAndSqueeze() {
        StubIndicatorCalculator indicatorCalculator = new StubIndicatorCalculator();
        indicatorCalculator.atr = bd("2");
        indicatorCalculator.realizedVolatility = bd("0.05");
        indicatorCalculator.managedAllocation = bd("0.40");
        indicatorCalculator.simpleMovingAverage = bd("100");
        indicatorCalculator.bollingerUpper = bd("104");
        indicatorCalculator.bollingerLower = bd("96");
        StrategyFeatureLibrary featureLibrary = new StrategyFeatureLibrary(indicatorCalculator);

        StrategyFeatureLibrary.VolatilityFilterSnapshot snapshot = featureLibrary.volatilityFilter(
            createFlatCandles(40, bd("100"), bd("1000")),
            25,
            new StrategyFeatureLibrary.VolatilityFilterSpec(
                20,
                20,
                bd("0.02"),
                bd("0.35"),
                bd("3"),
                20,
                bd("2"),
                bd("10")
            )
        );

        assertEquals(0, bd("2").compareTo(snapshot.atrPercent()));
        assertEquals(0, bd("8").compareTo(snapshot.bandWidthPercent()));
        assertEquals(0, bd("0.40").compareTo(snapshot.managedAllocation()));
        assertTrue(snapshot.passesAtrCap());
        assertTrue(snapshot.squeezeActive());
    }

    @Test
    void classifyRegime_returnsWarmupRangeAndTrendStates() {
        StubIndicatorCalculator indicatorCalculator = new StubIndicatorCalculator();
        indicatorCalculator.exponentialMovingAverage = bd("100");
        StrategyFeatureLibrary featureLibrary = new StrategyFeatureLibrary(indicatorCalculator);
        List<OHLCVData> candles = createFlatCandles(240, bd("101"), bd("1000"));

        StrategyFeatureLibrary.RegimeSnapshot warmup = featureLibrary.classifyRegime(
            candles,
            100,
            new StrategyFeatureLibrary.RegimeClassifierSpec(200, 14, bd("20"))
        );
        indicatorCalculator.averageDirectionalIndex = bd("15");
        StrategyFeatureLibrary.RegimeSnapshot range = featureLibrary.classifyRegime(
            candles,
            199,
            new StrategyFeatureLibrary.RegimeClassifierSpec(200, 14, bd("20"))
        );
        indicatorCalculator.averageDirectionalIndex = bd("25");
        StrategyFeatureLibrary.RegimeSnapshot trendUp = featureLibrary.classifyRegime(
            candles,
            200,
            new StrategyFeatureLibrary.RegimeClassifierSpec(200, 14, bd("20"))
        );
        candles.set(201, candle(LocalDateTime.parse("2025-01-09T09:00:00"), bd("95"), bd("1000")));
        StrategyFeatureLibrary.RegimeSnapshot trendDown = featureLibrary.classifyRegime(
            candles,
            201,
            new StrategyFeatureLibrary.RegimeClassifierSpec(200, 14, bd("20"))
        );

        assertEquals(StrategyFeatureLibrary.StrategyMarketRegime.WARMUP, warmup.regime());
        assertEquals(StrategyFeatureLibrary.StrategyMarketRegime.RANGE, range.regime());
        assertEquals(StrategyFeatureLibrary.StrategyMarketRegime.TREND_UP, trendUp.regime());
        assertEquals(StrategyFeatureLibrary.StrategyMarketRegime.TREND_DOWN, trendDown.regime());
    }

    @Test
    void longRiskLevels_prefersClosestProtectiveStop() {
        StubIndicatorCalculator indicatorCalculator = new StubIndicatorCalculator();
        indicatorCalculator.atr = bd("4");
        StrategyFeatureLibrary featureLibrary = new StrategyFeatureLibrary(indicatorCalculator);

        StrategyFeatureLibrary.LongRiskLevels riskLevels = featureLibrary.longRiskLevels(
            createFlatCandles(40, bd("100"), bd("1000")),
            20,
            bd("100"),
            new StrategyFeatureLibrary.LongRiskSpec(14, bd("1.50"), bd("0.93")),
            bd("96")
        );

        assertEquals(0, bd("94").compareTo(riskLevels.atrStop()));
        assertEquals(0, bd("96").compareTo(riskLevels.effectiveStop()));
        assertEquals(0, bd("4").compareTo(riskLevels.stopDistance()));
    }

    @Test
    void sessionAnchors_computeOpeningRangeVwapAndCutoff() {
        StrategyFeatureLibrary featureLibrary = new StrategyFeatureLibrary(new BacktestIndicatorCalculator());
        List<OHLCVData> candles = List.of(
            candle(LocalDateTime.parse("2025-01-02T09:30:00"), bd("100"), bd("100")),
            candle(LocalDateTime.parse("2025-01-02T10:00:00"), bd("102"), bd("100")),
            candle(LocalDateTime.parse("2025-01-02T10:30:00"), bd("103"), bd("100")),
            candle(LocalDateTime.parse("2025-01-02T11:00:00"), bd("101"), bd("100")),
            candle(LocalDateTime.parse("2025-01-02T11:30:00"), bd("104"), bd("100"))
        );

        StrategyFeatureLibrary.SessionAnchorSnapshot anchors = featureLibrary.sessionAnchors(
            candles,
            4,
            new StrategyFeatureLibrary.SessionAnchorSpec(2, LocalTime.of(11, 0))
        );

        assertEquals(LocalDateTime.parse("2025-01-02T09:30:00"), anchors.sessionStart());
        assertEquals(5, anchors.barsSinceOpen());
        assertEquals(0, bd("103").compareTo(anchors.openingRangeHigh()));
        assertEquals(0, bd("99").compareTo(anchors.openingRangeLow()));
        assertEquals(0, bd("102.0").compareTo(anchors.sessionVwap()));
        assertTrue(anchors.afterCutoff());
    }

    private List<OHLCVData> createFlatCandles(int count, BigDecimal close, BigDecimal volume) {
        List<BigDecimal> volumes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            volumes.add(volume);
        }
        return createFlatCandles(count, close, volumes);
    }

    private List<OHLCVData> createFlatCandles(int count, BigDecimal close, List<BigDecimal> volumes) {
        List<OHLCVData> candles = new ArrayList<>(count);
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        for (int index = 0; index < count; index++) {
            candles.add(candle(start.plusHours(index), close, volumes.get(index)));
        }
        return candles;
    }

    private OHLCVData candle(LocalDateTime timestamp, BigDecimal close, BigDecimal volume) {
        return new OHLCVData(
            timestamp,
            "BTC/USDT",
            close,
            close.add(BigDecimal.ONE),
            close.subtract(BigDecimal.ONE),
            close,
            volume
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class RecordingIndicatorCalculator extends BacktestIndicatorCalculator {

        private final List<String> emaRequests = new ArrayList<>();

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            emaRequests.add(period + ":" + endIndex);
            return switch (period) {
                case 200 -> endIndex == 205 ? new BigDecimal("100") : new BigDecimal("99");
                case 50 -> endIndex == 205 ? new BigDecimal("105") : new BigDecimal("104");
                case 20 -> endIndex == 205 ? new BigDecimal("110") : new BigDecimal("109");
                default -> throw new IllegalArgumentException("Unexpected period: " + period);
            };
        }

        private List<String> emaRequests() {
            return emaRequests;
        }
    }

    private static final class StubIndicatorCalculator extends BacktestIndicatorCalculator {

        private BigDecimal exponentialMovingAverage = BigDecimal.ZERO;
        private BigDecimal averageDirectionalIndex = BigDecimal.ZERO;
        private BigDecimal atr = BigDecimal.ZERO;
        private BigDecimal realizedVolatility = BigDecimal.ZERO;
        private BigDecimal managedAllocation = BigDecimal.ZERO;
        private BigDecimal simpleMovingAverage = BigDecimal.ZERO;
        private BigDecimal bollingerUpper = BigDecimal.ZERO;
        private BigDecimal bollingerLower = BigDecimal.ZERO;

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return exponentialMovingAverage;
        }

        @Override
        public BigDecimal averageDirectionalIndex(List<OHLCVData> candles, int endIndex, int period) {
            return averageDirectionalIndex;
        }

        @Override
        public BigDecimal averageTrueRange(List<OHLCVData> candles, int endIndex, int period) {
            return atr;
        }

        @Override
        public BigDecimal realizedVolatility(List<OHLCVData> candles, int endIndex, int period) {
            return realizedVolatility;
        }

        @Override
        public BigDecimal volatilityAdjustedAllocation(List<OHLCVData> candles,
                                                       int endIndex,
                                                       int period,
                                                       BigDecimal targetVolatility,
                                                       BigDecimal minimumAllocation) {
            return managedAllocation;
        }

        @Override
        public BigDecimal simpleMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return simpleMovingAverage;
        }

        @Override
        public BigDecimal bollingerUpperBand(List<OHLCVData> candles, int endIndex, int period, BigDecimal multiplier) {
            return bollingerUpper;
        }

        @Override
        public BigDecimal bollingerLowerBand(List<OHLCVData> candles, int endIndex, int period, BigDecimal multiplier) {
            return bollingerLower;
        }
    }
}
