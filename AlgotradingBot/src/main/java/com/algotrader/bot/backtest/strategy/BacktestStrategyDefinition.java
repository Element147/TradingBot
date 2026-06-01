package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;

import java.util.List;
import java.util.Map;

public record BacktestStrategyDefinition(
    BacktestAlgorithmType type,
    String label,
    String description,
    BacktestStrategySelectionMode selectionMode,
    int minimumCandles,
    Map<String, List<String>> parameterGrid
) {
    public BacktestStrategyDefinition(
        BacktestAlgorithmType type,
        String label,
        String description,
        BacktestStrategySelectionMode selectionMode,
        int minimumCandles
    ) {
        this(type, label, description, selectionMode, minimumCandles, Map.of());
    }
}

