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
public class DualMomentumRotationBacktestStrategy implements BacktestStrategy {

    private static final int SHORT_LOOKBACK = 63;
    private static final int LONG_LOOKBACK = 126;
    private static final int ABSOLUTE_FILTER_PERIOD = 200;
    private static final int VOL_LOOKBACK = 20;
    private static final BigDecimal SHORT_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal LONG_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal TARGET_VOL = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.40");
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION,
        "Dual Momentum Rotation",
        "Ranks the strongest assets in the dataset universe and rotates into the top symbol only when absolute momentum is positive.",
        BacktestStrategySelectionMode.DATASET_UNIVERSE,
        ABSOLUTE_FILTER_PERIOD
    );

    private final BacktestIndicatorCalculator indicatorCalculator;

    public DualMomentumRotationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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
            .max(Comparator.comparing(symbol -> relativeMomentumScore(context.candles(symbol), context.currentIndex(symbol))));

        if (bestSymbol.isEmpty()) {
            return BacktestStrategyDecision.hold();
        }

        String topSymbol = bestSymbol.get();
        List<OHLCVData> topCandles = context.candles(topSymbol);
        int topIndex = context.currentIndex(topSymbol);

        if (!absoluteMomentumIsPositive(topCandles, topIndex)) {
            return context.inPosition()
                ? BacktestStrategyDecision.sell("Absolute momentum turned negative, rotate to cash")
                : BacktestStrategyDecision.hold();
        }

        BigDecimal allocation = indicatorCalculator.volatilityAdjustedAllocation(
            topCandles,
            topIndex,
            VOL_LOOKBACK,
            TARGET_VOL,
            MIN_ALLOCATION
        );

        if (!context.inPosition()) {
            return BacktestStrategyDecision.buy(topSymbol, allocation, "Top-ranked symbol passed absolute momentum filter");
        }

        if (topSymbol.equalsIgnoreCase(context.activeSymbol())) {
            return BacktestStrategyDecision.hold();
        }

        return BacktestStrategyDecision.rotate(topSymbol, allocation, "Relative momentum leadership changed");
    }

    private BigDecimal relativeMomentumScore(List<OHLCVData> candles, int index) {
        BigDecimal shortReturn = indicatorCalculator.rollingReturn(candles, index, SHORT_LOOKBACK);
        BigDecimal longReturn = indicatorCalculator.rollingReturn(candles, index, LONG_LOOKBACK);
        return shortReturn.multiply(SHORT_WEIGHT, MC).add(longReturn.multiply(LONG_WEIGHT, MC), MC);
    }

    private boolean absoluteMomentumIsPositive(List<OHLCVData> candles, int index) {
        BigDecimal close = candles.get(index).getClose();
        BigDecimal sma200 = indicatorCalculator.simpleMovingAverage(candles, index, ABSOLUTE_FILTER_PERIOD);
        BigDecimal longReturn = indicatorCalculator.rollingReturn(candles, index, LONG_LOOKBACK);
        return close.compareTo(sma200) > 0 || longReturn.compareTo(BigDecimal.ZERO) > 0;
    }
}
