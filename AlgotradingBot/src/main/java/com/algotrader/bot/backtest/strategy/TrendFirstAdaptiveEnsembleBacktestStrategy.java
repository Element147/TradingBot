package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class TrendFirstAdaptiveEnsembleBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 200;
    private static final int MOMENTUM_SHORT_LOOKBACK = 63;
    private static final int MOMENTUM_LONG_LOOKBACK = 126;
    private static final int DONCHIAN_BREAKOUT = 55;
    private static final int DONCHIAN_EXIT = 20;
    private static final int RSI_PERIOD = 5;
    private static final int BOLLINGER_PERIOD = 20;
    private static final BigDecimal BOLLINGER_MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal RANGE_ADX_THRESHOLD = new BigDecimal("20");
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE,
        "Trend-First Adaptive Ensemble",
        "Chooses the strongest symbol from the dataset universe, trades trend strategies in trending regimes, and only allows mean reversion in range conditions.",
        BacktestStrategySelectionMode.DATASET_UNIVERSE,
        LONG_TREND_PERIOD
    );

    private final BacktestIndicatorCalculator indicatorCalculator;

    public TrendFirstAdaptiveEnsembleBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this.indicatorCalculator = indicatorCalculator;
    }

    @Override
    public BacktestStrategyDefinition definition() {
        return DEFINITION;
    }

    @Override
    public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
        Optional<String> bestSymbol = context.symbols().stream()
            .filter(symbol -> context.hasEnoughCandles(symbol, getMinimumCandles()))
            .max(Comparator.comparing(symbol -> momentumScore(context.candles(symbol), context.currentIndex(symbol))));

        if (bestSymbol.isEmpty()) {
            return BacktestStrategyDecision.hold();
        }

        String targetSymbol = bestSymbol.get();
        List<OHLCVData> candles = context.candles(targetSymbol);
        int index = context.currentIndex(targetSymbol);

        BigDecimal close = candles.get(index).getClose();
        BigDecimal ema200 = indicatorCalculator.exponentialMovingAverage(candles, index, LONG_TREND_PERIOD);
        BigDecimal adx = indicatorCalculator.averageDirectionalIndex(candles, index, 14);
        BigDecimal breakoutLevel = indicatorCalculator.highestHigh(candles, index - 1, DONCHIAN_BREAKOUT);
        BigDecimal exitLevel = indicatorCalculator.lowestLow(candles, index - 1, DONCHIAN_EXIT);
        BigDecimal ema20 = indicatorCalculator.exponentialMovingAverage(candles, index, 20);
        BigDecimal rsi = indicatorCalculator.relativeStrengthIndex(candles, index, RSI_PERIOD);
        BigDecimal lowerBand = indicatorCalculator.bollingerLowerBand(candles, index, BOLLINGER_PERIOD, BOLLINGER_MULTIPLIER);

        boolean trendUp = close.compareTo(ema200) > 0;
        boolean rangeRegime = adx.compareTo(RANGE_ADX_THRESHOLD) < 0;
        boolean breakoutTriggered = close.compareTo(breakoutLevel) > 0;
        boolean pullbackContinuation = close.compareTo(ema20) > 0 && rsi.compareTo(new BigDecimal("40")) > 0;
        boolean meanReversionEntry = close.compareTo(lowerBand) < 0 && rsi.compareTo(new BigDecimal("25")) < 0;

        if (!context.inPosition()) {
            if (trendUp && (breakoutTriggered || pullbackContinuation)) {
                BigDecimal allocation = indicatorCalculator.volatilityAdjustedAllocation(
                    candles,
                    index,
                    20,
                    new BigDecimal("0.02"),
                    new BigDecimal("0.40")
                );
                return BacktestStrategyDecision.buy(targetSymbol, allocation, "Trend regime routed to breakout/pullback layer");
            }

            if (rangeRegime && meanReversionEntry) {
                return BacktestStrategyDecision.buy(targetSymbol, new BigDecimal("0.50"), "Range regime routed to mean reversion layer");
            }

            return BacktestStrategyDecision.hold();
        }

        if (!context.activeSymbol().equalsIgnoreCase(targetSymbol) && trendUp) {
            BigDecimal allocation = indicatorCalculator.volatilityAdjustedAllocation(
                candles,
                index,
                20,
                new BigDecimal("0.02"),
                new BigDecimal("0.40")
            );
            return BacktestStrategyDecision.rotate(targetSymbol, allocation, "Leadership rotated to a stronger symbol");
        }

        List<OHLCVData> activeCandles = context.candles(context.activeSymbol());
        int activeIndex = context.currentIndex(context.activeSymbol());
        BigDecimal activeClose = activeCandles.get(activeIndex).getClose();
        BigDecimal activeEma200 = indicatorCalculator.exponentialMovingAverage(activeCandles, activeIndex, LONG_TREND_PERIOD);
        BigDecimal activeMiddleBand = indicatorCalculator.simpleMovingAverage(activeCandles, activeIndex, BOLLINGER_PERIOD);
        BigDecimal activeExitLevel = indicatorCalculator.lowestLow(activeCandles, activeIndex - 1, DONCHIAN_EXIT);

        if (activeClose.compareTo(activeEma200) < 0 || activeClose.compareTo(activeExitLevel) < 0) {
            return BacktestStrategyDecision.sell("Trend layer exited on regime deterioration");
        }

        if (rangeRegime && activeClose.compareTo(activeMiddleBand) >= 0) {
            return BacktestStrategyDecision.sell("Range layer mean-reversion target reached");
        }

        return BacktestStrategyDecision.hold();
    }

    private BigDecimal momentumScore(List<OHLCVData> candles, int index) {
        BigDecimal shortReturn = indicatorCalculator.rollingReturn(candles, index, MOMENTUM_SHORT_LOOKBACK);
        BigDecimal longReturn = indicatorCalculator.rollingReturn(candles, index, MOMENTUM_LONG_LOOKBACK);
        return shortReturn.multiply(new BigDecimal("0.6"), MC)
            .add(longReturn.multiply(new BigDecimal("0.4"), MC), MC);
    }
}
