package com.algotrader.bot.backtest;

import java.time.LocalDateTime;

public record BacktestSimulationProgress(
    int processedCandles,
    int totalCandles,
    LocalDateTime currentTimestamp
) {
}
