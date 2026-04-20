package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class StrategyFeatureLibrary {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int DEFAULT_SESSION_OPENING_RANGE_BARS = 4;

    private final BacktestIndicatorCalculator indicatorCalculator;

    public StrategyFeatureLibrary(BacktestIndicatorCalculator indicatorCalculator) {
        this.indicatorCalculator = indicatorCalculator;
    }

    public TrendFilterSnapshot trendFilter(List<OHLCVData> candles, int index, TrendFilterSpec spec) {
        BigDecimal close = candles.get(index).getClose();
        BigDecimal longLine = indicatorCalculator.exponentialMovingAverage(candles, index, spec.longPeriod());
        BigDecimal mediumLine = spec.mediumPeriod() > 0
            ? indicatorCalculator.exponentialMovingAverage(candles, index, spec.mediumPeriod())
            : longLine;
        BigDecimal fastLine = spec.fastPeriod() > 0
            ? indicatorCalculator.exponentialMovingAverage(candles, index, spec.fastPeriod())
            : mediumLine;

        BigDecimal previousLongLine = index > 0 && index >= spec.longPeriod()
            ? indicatorCalculator.exponentialMovingAverage(candles, index - 1, spec.longPeriod())
            : longLine;
        BigDecimal previousMediumLine = spec.mediumPeriod() > 0 && index > 0 && index >= spec.mediumPeriod()
            ? indicatorCalculator.exponentialMovingAverage(candles, index - 1, spec.mediumPeriod())
            : mediumLine;
        BigDecimal previousFastLine = spec.fastPeriod() > 0 && index > 0 && index >= spec.fastPeriod()
            ? indicatorCalculator.exponentialMovingAverage(candles, index - 1, spec.fastPeriod())
            : fastLine;

        BigDecimal floorLine = longLine.multiply(spec.floorMultiplier(), MC);

        boolean longTrendUp = longLine.compareTo(previousLongLine) >= 0;
        boolean mediumTrendUp = mediumLine.compareTo(previousMediumLine) >= 0;
        boolean fastTrendUp = fastLine.compareTo(previousFastLine) >= 0;
        boolean aboveLongLine = close.compareTo(longLine) >= 0;
        boolean aboveFloor = close.compareTo(floorLine) >= 0;
        boolean alignedBullish = aboveFloor
            && mediumLine.compareTo(longLine) >= 0
            && fastLine.compareTo(mediumLine) >= 0;

        return new TrendFilterSnapshot(
            close,
            longLine,
            mediumLine,
            fastLine,
            floorLine,
            longTrendUp,
            mediumTrendUp,
            fastTrendUp,
            aboveLongLine,
            aboveFloor,
            alignedBullish
        );
    }

    public VolumeConfirmationSnapshot volumeConfirmation(List<OHLCVData> candles, int index, VolumeConfirmationSpec spec) {
        BigDecimal currentVolume = candles.get(index).getVolume();
        BigDecimal averageVolume = indicatorCalculator.averageVolume(candles, index, spec.averagePeriod());
        BigDecimal ratio = averageVolume.compareTo(ZERO) == 0
            ? ZERO
            : currentVolume.divide(averageVolume, MC);
        boolean confirmed = ratio.compareTo(spec.minimumRatio()) >= 0;

        return new VolumeConfirmationSnapshot(currentVolume, averageVolume, ratio, confirmed);
    }

    public VolatilityFilterSnapshot volatilityFilter(List<OHLCVData> candles, int index, VolatilityFilterSpec spec) {
        BigDecimal close = candles.get(index).getClose();
        BigDecimal atr = indicatorCalculator.averageTrueRange(candles, index, spec.atrPeriod());
        BigDecimal realizedVolatility = indicatorCalculator.realizedVolatility(candles, index, spec.realizedVolatilityPeriod());
        BigDecimal allocation = indicatorCalculator.volatilityAdjustedAllocation(
            candles,
            index,
            spec.realizedVolatilityPeriod(),
            spec.targetVolatility(),
            spec.minimumAllocation()
        );
        BigDecimal atrPercent = close.compareTo(ZERO) == 0
            ? ZERO
            : atr.divide(close, MC).multiply(HUNDRED, MC);

        BigDecimal bandWidthPercent = null;
        if (spec.squeezePeriod() > 1 && spec.squeezeMultiplier() != null) {
            BigDecimal bollingerMiddle = indicatorCalculator.simpleMovingAverage(candles, index, spec.squeezePeriod());
            BigDecimal bollingerUpper = indicatorCalculator.bollingerUpperBand(
                candles,
                index,
                spec.squeezePeriod(),
                spec.squeezeMultiplier()
            );
            BigDecimal bollingerLower = indicatorCalculator.bollingerLowerBand(
                candles,
                index,
                spec.squeezePeriod(),
                spec.squeezeMultiplier()
            );
            bandWidthPercent = bollingerMiddle.compareTo(ZERO) == 0
                ? ZERO
                : bollingerUpper.subtract(bollingerLower, MC)
                    .divide(bollingerMiddle, MC)
                    .multiply(HUNDRED, MC);
        }

        boolean passesAtrCap = spec.maximumAtrPercent() == null || atrPercent.compareTo(spec.maximumAtrPercent()) <= 0;
        boolean squeezeActive = bandWidthPercent != null
            && spec.maximumSqueezeWidthPercent() != null
            && bandWidthPercent.compareTo(spec.maximumSqueezeWidthPercent()) <= 0;

        return new VolatilityFilterSnapshot(
            atr,
            realizedVolatility,
            allocation,
            atrPercent,
            bandWidthPercent,
            passesAtrCap,
            squeezeActive
        );
    }

    public RegimeSnapshot classifyRegime(List<OHLCVData> candles, int index, RegimeClassifierSpec spec) {
        int minimumIndex = Math.max(spec.trendPeriod() - 1, spec.adxPeriod());
        if (index < minimumIndex) {
            return new RegimeSnapshot(
                StrategyMarketRegime.WARMUP,
                null,
                null,
                null,
                false,
                false
            );
        }

        BigDecimal close = candles.get(index).getClose();
        BigDecimal trendLine = indicatorCalculator.exponentialMovingAverage(candles, index, spec.trendPeriod());
        BigDecimal adx = indicatorCalculator.averageDirectionalIndex(candles, index, spec.adxPeriod());

        if (adx.compareTo(spec.rangeThreshold()) < 0) {
            return new RegimeSnapshot(
                StrategyMarketRegime.RANGE,
                close,
                trendLine,
                adx,
                false,
                true
            );
        }

        if (close.compareTo(trendLine) >= 0) {
            return new RegimeSnapshot(
                StrategyMarketRegime.TREND_UP,
                close,
                trendLine,
                adx,
                true,
                false
            );
        }

        return new RegimeSnapshot(
            StrategyMarketRegime.TREND_DOWN,
            close,
            trendLine,
            adx,
            false,
            false
        );
    }

    public LongRiskLevels longRiskLevels(List<OHLCVData> candles,
                                         int index,
                                         BigDecimal entryPrice,
                                         LongRiskSpec spec,
                                         BigDecimal structuralSupport) {
        BigDecimal atr = indicatorCalculator.averageTrueRange(candles, index, spec.atrPeriod());
        BigDecimal atrStop = entryPrice.subtract(atr.multiply(spec.atrStopMultiple(), MC), MC);
        BigDecimal hardStop = spec.hardStopMultiplier() == null
            ? null
            : entryPrice.multiply(spec.hardStopMultiplier(), MC);
        BigDecimal effectiveStop = maxNonNull(atrStop, structuralSupport, hardStop);
        BigDecimal stopDistance = entryPrice.subtract(effectiveStop, MC).max(ZERO);

        return new LongRiskLevels(atr, atrStop, structuralSupport, hardStop, effectiveStop, stopDistance);
    }

    public SessionAnchorSnapshot sessionAnchors(List<OHLCVData> candles, int index, SessionAnchorSpec spec) {
        if (candles.isEmpty() || index < 0) {
            throw new IllegalArgumentException("Session anchors require an active candle index");
        }

        int sessionStartIndex = resolveSessionStartIndex(candles, index);
        int openingRangeEndIndex = Math.min(
            index,
            sessionStartIndex + Math.max(1, spec.openingRangeBars()) - 1
        );
        BigDecimal openingRangeHigh = candles.get(sessionStartIndex).getHigh();
        BigDecimal openingRangeLow = candles.get(sessionStartIndex).getLow();
        BigDecimal weightedPriceVolume = ZERO;
        BigDecimal totalVolume = ZERO;

        for (int candleIndex = sessionStartIndex; candleIndex <= index; candleIndex++) {
            OHLCVData candle = candles.get(candleIndex);
            BigDecimal typicalPrice = candle.getHigh()
                .add(candle.getLow(), MC)
                .add(candle.getClose(), MC)
                .divide(THREE, MC);

            weightedPriceVolume = weightedPriceVolume.add(typicalPrice.multiply(candle.getVolume(), MC), MC);
            totalVolume = totalVolume.add(candle.getVolume(), MC);

            if (candleIndex <= openingRangeEndIndex) {
                openingRangeHigh = openingRangeHigh.max(candle.getHigh());
                openingRangeLow = openingRangeLow.min(candle.getLow());
            }
        }

        OHLCVData currentCandle = candles.get(index);
        BigDecimal sessionVwap = totalVolume.compareTo(ZERO) == 0
            ? currentCandle.getClose()
            : weightedPriceVolume.divide(totalVolume, MC);
        boolean afterCutoff = !currentCandle.getTimestamp().toLocalTime().isBefore(spec.cutoffTime());

        return new SessionAnchorSnapshot(
            candles.get(sessionStartIndex).getTimestamp(),
            currentCandle.getTimestamp().toLocalDate(),
            index - sessionStartIndex + 1,
            sessionVwap,
            openingRangeHigh,
            openingRangeLow,
            afterCutoff
        );
    }

    private int resolveSessionStartIndex(List<OHLCVData> candles, int index) {
        LocalDate sessionDate = candles.get(index).getTimestamp().toLocalDate();
        int sessionStartIndex = index;
        while (sessionStartIndex > 0) {
            LocalDate previousDate = candles.get(sessionStartIndex - 1).getTimestamp().toLocalDate();
            if (!previousDate.equals(sessionDate)) {
                break;
            }
            sessionStartIndex--;
        }
        return sessionStartIndex;
    }

    private BigDecimal maxNonNull(BigDecimal first, BigDecimal second, BigDecimal third) {
        BigDecimal result = first;
        if (second != null && second.compareTo(result) > 0) {
            result = second;
        }
        if (third != null && third.compareTo(result) > 0) {
            result = third;
        }
        return result;
    }

    public record TrendFilterSpec(
        int longPeriod,
        int mediumPeriod,
        int fastPeriod,
        BigDecimal floorMultiplier
    ) {
        public TrendFilterSpec {
            floorMultiplier = floorMultiplier == null ? ONE : floorMultiplier;
        }
    }

    public record TrendFilterSnapshot(
        BigDecimal close,
        BigDecimal longLine,
        BigDecimal mediumLine,
        BigDecimal fastLine,
        BigDecimal floorLine,
        boolean longTrendUp,
        boolean mediumTrendUp,
        boolean fastTrendUp,
        boolean aboveLongLine,
        boolean aboveFloor,
        boolean alignedBullish
    ) {
    }

    public record VolumeConfirmationSpec(
        int averagePeriod,
        BigDecimal minimumRatio
    ) {
    }

    public record VolumeConfirmationSnapshot(
        BigDecimal currentVolume,
        BigDecimal averageVolume,
        BigDecimal ratio,
        boolean confirmed
    ) {
    }

    public record VolatilityFilterSpec(
        int atrPeriod,
        int realizedVolatilityPeriod,
        BigDecimal targetVolatility,
        BigDecimal minimumAllocation,
        BigDecimal maximumAtrPercent,
        int squeezePeriod,
        BigDecimal squeezeMultiplier,
        BigDecimal maximumSqueezeWidthPercent
    ) {
    }

    public record VolatilityFilterSnapshot(
        BigDecimal atr,
        BigDecimal realizedVolatility,
        BigDecimal managedAllocation,
        BigDecimal atrPercent,
        BigDecimal bandWidthPercent,
        boolean passesAtrCap,
        boolean squeezeActive
    ) {
    }

    public record RegimeClassifierSpec(
        int trendPeriod,
        int adxPeriod,
        BigDecimal rangeThreshold
    ) {
    }

    public record RegimeSnapshot(
        StrategyMarketRegime regime,
        BigDecimal close,
        BigDecimal trendLine,
        BigDecimal adx,
        boolean trendUp,
        boolean rangeBound
    ) {
    }

    public record LongRiskSpec(
        int atrPeriod,
        BigDecimal atrStopMultiple,
        BigDecimal hardStopMultiplier
    ) {
    }

    public record LongRiskLevels(
        BigDecimal atr,
        BigDecimal atrStop,
        BigDecimal structuralStop,
        BigDecimal hardStop,
        BigDecimal effectiveStop,
        BigDecimal stopDistance
    ) {
    }

    public record SessionAnchorSpec(
        int openingRangeBars,
        LocalTime cutoffTime
    ) {
        public SessionAnchorSpec {
            openingRangeBars = openingRangeBars <= 0 ? DEFAULT_SESSION_OPENING_RANGE_BARS : openingRangeBars;
            cutoffTime = cutoffTime == null ? LocalTime.of(23, 59) : cutoffTime;
        }
    }

    public record SessionAnchorSnapshot(
        LocalDateTime sessionStart,
        LocalDate sessionDate,
        int barsSinceOpen,
        BigDecimal sessionVwap,
        BigDecimal openingRangeHigh,
        BigDecimal openingRangeLow,
        boolean afterCutoff
    ) {
    }

    public enum StrategyMarketRegime {
        WARMUP,
        RANGE,
        TREND_UP,
        TREND_DOWN
    }
}
