package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SmaCrossoverBacktestStrategy implements BacktestStrategy {

    private static final int FAST_PERIOD = 10;
    private static final int SLOW_PERIOD = 30;
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.SMA_CROSSOVER,
        "SMA Crossover",
        "Classic fast/slow moving average crossover trend-following strategy.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        SLOW_PERIOD + 1
    );

    private final BacktestIndicatorCalculator indicatorCalculator;

    public SmaCrossoverBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this.indicatorCalculator = indicatorCalculator;
    }

    @Override
    public BacktestStrategyDefinition definition() {
        return DEFINITION;
    }

    @Override
    public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
        if (context.currentIndex() < SLOW_PERIOD) {
            return BacktestStrategyDecision.hold();
        }

        BigDecimal fastPrevious = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex() - 1, FAST_PERIOD);
        BigDecimal slowPrevious = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex() - 1, SLOW_PERIOD);
        BigDecimal fastCurrent = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex(), FAST_PERIOD);
        BigDecimal slowCurrent = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex(), SLOW_PERIOD);
        boolean bullishCross = fastPrevious.compareTo(slowPrevious) <= 0
            && fastCurrent.compareTo(slowCurrent) > 0;
        boolean bearishCross = fastPrevious.compareTo(slowPrevious) >= 0
            && fastCurrent.compareTo(slowCurrent) < 0;

        if (!context.inPosition() && bullishCross) {
            return BacktestStrategyDecision.buy(context.primarySymbol(), BigDecimal.ONE, "Fast SMA crossed above slow SMA");
        }

        if (!context.inPosition() && bearishCross) {
            return BacktestStrategyDecision.shortSell(context.primarySymbol(), BigDecimal.ONE, "Fast SMA crossed below slow SMA");
        }

        if (context.inLongPosition() && bearishCross) {
            return BacktestStrategyDecision.sell("Fast SMA crossed below slow SMA");
        }

        if (context.inShortPosition() && bullishCross) {
            return BacktestStrategyDecision.cover("Fast SMA crossed above slow SMA");
        }

        return BacktestStrategyDecision.hold();
    }
}
