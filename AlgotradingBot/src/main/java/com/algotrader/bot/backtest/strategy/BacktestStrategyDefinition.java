package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;

public record BacktestStrategyDefinition(
    BacktestAlgorithmType type,
    String label,
    String description,
    BacktestStrategySelectionMode selectionMode,
    int minimumCandles
) {
}
