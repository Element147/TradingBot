package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class SmaCrossoverBacktestStrategy implements BacktestStrategy {

    private static final int FAST_PERIOD = 10;
    private static final int SLOW_PERIOD = 30;


    private static final Map<String, List<String>> PARAMETER_GRID = Map.of(
        "fastPeriod", List.of("5", "10", "15", "20"),
        "slowPeriod", List.of("20", "30", "40", "50")
    );

    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.SMA_CROSSOVER,
        "SMA Crossover",
        "Classic fast/slow moving average crossover trend-following strategy.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        51,
        PARAMETER_GRID
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
        int fastPeriod = Integer.parseInt(context.parameters().getOrDefault("fastPeriod", String.valueOf(FAST_PERIOD)));
        int slowPeriod = Integer.parseInt(context.parameters().getOrDefault("slowPeriod", String.valueOf(SLOW_PERIOD)));

        if (context.currentIndex() < slowPeriod) {
            return BacktestStrategyDecision.hold();
        }

        BigDecimal fastPrevious = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex() - 1, fastPeriod);
        BigDecimal slowPrevious = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex() - 1, slowPeriod);
        BigDecimal fastCurrent = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex(), fastPeriod);
        BigDecimal slowCurrent = indicatorCalculator.simpleMovingAverage(context.candles(), context.currentIndex(), slowPeriod);
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
