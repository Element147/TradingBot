package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class BacktestIndicatorCalculator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THOUSANDTH = new BigDecimal("0.001");

    public BigDecimal simpleMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum = sum.add(candles.get(i).getClose(), MC);
        }
        return sum.divide(BigDecimal.valueOf(period), MC);
    }

    public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal multiplier = TWO.divide(BigDecimal.valueOf(period + 1L), MC);
        int startIndex = endIndex - period + 1;
        BigDecimal ema = simpleMovingAverage(candles, startIndex + period - 1, period);

        for (int i = startIndex + period; i <= endIndex; i++) {
            BigDecimal close = candles.get(i).getClose();
            ema = close.subtract(ema, MC).multiply(multiplier, MC).add(ema, MC);
        }

        return ema;
    }

    public BigDecimal standardDeviation(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal mean = simpleMovingAverage(candles, endIndex, period);
        BigDecimal variance = BigDecimal.ZERO;

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            BigDecimal delta = candles.get(i).getClose().subtract(mean, MC);
            variance = variance.add(delta.multiply(delta, MC), MC);
        }

        return sqrt(variance.divide(BigDecimal.valueOf(period), MC));
    }

    public BigDecimal bollingerLowerBand(List<OHLCVData> candles, int endIndex, int period, BigDecimal multiplier) {
        BigDecimal mean = simpleMovingAverage(candles, endIndex, period);
        return mean.subtract(standardDeviation(candles, endIndex, period).multiply(multiplier, MC), MC);
    }

    public BigDecimal bollingerUpperBand(List<OHLCVData> candles, int endIndex, int period, BigDecimal multiplier) {
        BigDecimal mean = simpleMovingAverage(candles, endIndex, period);
        return mean.add(standardDeviation(candles, endIndex, period).multiply(multiplier, MC), MC);
    }

    public BigDecimal midpointChannel(List<OHLCVData> candles, int endIndex, int period) {
        return highestHigh(candles, endIndex, period)
            .add(lowestLow(candles, endIndex, period), MC)
            .divide(TWO, MC);
    }

    public BigDecimal ichimokuConversionLine(List<OHLCVData> candles, int endIndex) {
        return midpointChannel(candles, endIndex, 9);
    }

    public BigDecimal ichimokuBaseLine(List<OHLCVData> candles, int endIndex) {
        return midpointChannel(candles, endIndex, 26);
    }

    public BigDecimal ichimokuLeadingSpanA(List<OHLCVData> candles, int sourceIndex) {
        return ichimokuConversionLine(candles, sourceIndex)
            .add(ichimokuBaseLine(candles, sourceIndex), MC)
            .divide(TWO, MC);
    }

    public BigDecimal ichimokuLeadingSpanB(List<OHLCVData> candles, int sourceIndex) {
        return midpointChannel(candles, sourceIndex, 52);
    }

    public BigDecimal ichimokuLeadingSpanAAtCurrent(List<OHLCVData> candles, int currentIndex) {
        return ichimokuLeadingSpanAAtCurrent(candles, currentIndex, 26);
    }

    public BigDecimal ichimokuLeadingSpanAAtCurrent(List<OHLCVData> candles, int currentIndex, int displacement) {
        // The visible cloud at the current bar must come from a historical source index, not the current bar.
        return ichimokuLeadingSpanA(candles, currentIndex - displacement);
    }

    public BigDecimal ichimokuLeadingSpanBAtCurrent(List<OHLCVData> candles, int currentIndex) {
        return ichimokuLeadingSpanBAtCurrent(candles, currentIndex, 26);
    }

    public BigDecimal ichimokuLeadingSpanBAtCurrent(List<OHLCVData> candles, int currentIndex, int displacement) {
        return ichimokuLeadingSpanB(candles, currentIndex - displacement);
    }

    public BigDecimal highestHigh(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal highest = candles.get(endIndex - period + 1).getHigh();
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (candles.get(i).getHigh().compareTo(highest) > 0) {
                highest = candles.get(i).getHigh();
            }
        }
        return highest;
    }

    public BigDecimal lowestLow(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal lowest = candles.get(endIndex - period + 1).getLow();
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (candles.get(i).getLow().compareTo(lowest) < 0) {
                lowest = candles.get(i).getLow();
            }
        }
        return lowest;
    }

    public BigDecimal averageTrueRange(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            total = total.add(trueRange(candles, i), MC);
        }
        return total.divide(BigDecimal.valueOf(period), MC);
    }

    public BigDecimal relativeStrengthIndex(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            BigDecimal delta = candles.get(i).getClose().subtract(candles.get(i - 1).getClose(), MC);
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                gains = gains.add(delta, MC);
            } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                losses = losses.add(delta.abs(), MC);
            }
        }

        if (losses.compareTo(BigDecimal.ZERO) == 0) {
            return HUNDRED;
        }

        BigDecimal averageGain = gains.divide(BigDecimal.valueOf(period), MC);
        BigDecimal averageLoss = losses.divide(BigDecimal.valueOf(period), MC);
        BigDecimal relativeStrength = averageGain.divide(averageLoss, MC);

        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(relativeStrength), MC), MC);
    }

    public BigDecimal averageDirectionalIndex(List<OHLCVData> candles, int endIndex, int period) {
        List<BigDecimal> trueRanges = new ArrayList<>();
        List<BigDecimal> positiveDirectionalMoves = new ArrayList<>();
        List<BigDecimal> negativeDirectionalMoves = new ArrayList<>();

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            OHLCVData current = candles.get(i);
            OHLCVData previous = candles.get(i - 1);

            BigDecimal upMove = current.getHigh().subtract(previous.getHigh(), MC);
            BigDecimal downMove = previous.getLow().subtract(current.getLow(), MC);

            positiveDirectionalMoves.add(upMove.compareTo(downMove) > 0 && upMove.compareTo(BigDecimal.ZERO) > 0
                ? upMove
                : BigDecimal.ZERO);
            negativeDirectionalMoves.add(downMove.compareTo(upMove) > 0 && downMove.compareTo(BigDecimal.ZERO) > 0
                ? downMove
                : BigDecimal.ZERO);
            trueRanges.add(trueRange(candles, i));
        }

        BigDecimal averageTrueRange = average(trueRanges);
        if (averageTrueRange.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal plusDi = average(positiveDirectionalMoves)
            .divide(averageTrueRange, MC)
            .multiply(HUNDRED, MC);
        BigDecimal minusDi = average(negativeDirectionalMoves)
            .divide(averageTrueRange, MC)
            .multiply(HUNDRED, MC);

        BigDecimal denominator = plusDi.add(minusDi, MC);
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return plusDi.subtract(minusDi, MC)
            .abs()
            .divide(denominator, MC)
            .multiply(HUNDRED, MC);
    }

    public BigDecimal rollingReturn(List<OHLCVData> candles, int endIndex, int lookback) {
        BigDecimal currentClose = candles.get(endIndex).getClose();
        BigDecimal previousClose = candles.get(endIndex - lookback).getClose();
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentClose.subtract(previousClose, MC).divide(previousClose, MC);
    }

    public BigDecimal realizedVolatility(List<OHLCVData> candles, int endIndex, int period) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            BigDecimal previousClose = candles.get(i - 1).getClose();
            if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
                returns.add(BigDecimal.ZERO);
                continue;
            }
            BigDecimal candleReturn = candles.get(i).getClose()
                .subtract(previousClose, MC)
                .divide(previousClose, MC);
            returns.add(candleReturn);
        }

        BigDecimal averageReturn = average(returns);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal candleReturn : returns) {
            BigDecimal delta = candleReturn.subtract(averageReturn, MC);
            variance = variance.add(delta.multiply(delta, MC), MC);
        }

        BigDecimal normalizedVariance = variance.divide(BigDecimal.valueOf(Math.max(1, returns.size() - 1)), MC);
        return sqrt(normalizedVariance).max(THOUSANDTH);
    }

    public BigDecimal averageVolume(List<OHLCVData> candles, int endIndex, int period) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            total = total.add(candles.get(i).getVolume(), MC);
        }
        return total.divide(BigDecimal.valueOf(period), MC);
    }

    public BigDecimal volatilityAdjustedAllocation(List<OHLCVData> candles,
                                                   int endIndex,
                                                   int period,
                                                   BigDecimal targetVolatility,
                                                   BigDecimal minimumAllocation) {
        BigDecimal realizedVolatility = realizedVolatility(candles, endIndex, period);
        BigDecimal allocation = targetVolatility.divide(realizedVolatility, MC);
        if (allocation.compareTo(minimumAllocation) < 0) {
            return minimumAllocation;
        }
        return allocation.min(BigDecimal.ONE);
    }

    public BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal estimate = value;
        BigDecimal previous;
        do {
            previous = estimate;
            estimate = estimate.add(value.divide(estimate, MC), MC).divide(TWO, MC);
        } while (estimate.subtract(previous).abs().compareTo(new BigDecimal("0.00000001")) > 0);

        return estimate;
    }

    private BigDecimal trueRange(List<OHLCVData> candles, int index) {
        OHLCVData current = candles.get(index);
        OHLCVData previous = candles.get(index - 1);

        BigDecimal highLow = current.getHigh().subtract(current.getLow(), MC).abs();
        BigDecimal highClose = current.getHigh().subtract(previous.getClose(), MC).abs();
        BigDecimal lowClose = current.getLow().subtract(previous.getClose(), MC).abs();

        return highLow.max(highClose).max(lowClose);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value, MC);
        }
        return total.divide(BigDecimal.valueOf(values.size()), MC);
    }
}
