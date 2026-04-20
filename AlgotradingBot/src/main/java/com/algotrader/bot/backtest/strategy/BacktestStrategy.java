package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;

public interface BacktestStrategy {

    BacktestStrategyDefinition definition();

    default BacktestAlgorithmType getType() {
        return definition().type();
    }

    default String getLabel() {
        return definition().label();
    }

    default String getDescription() {
        return definition().description();
    }

    default BacktestStrategySelectionMode getSelectionMode() {
        return definition().selectionMode();
    }

    default int getMinimumCandles() {
        return definition().minimumCandles();
    }

    BacktestStrategyDecision evaluate(BacktestStrategyContext context);
}
