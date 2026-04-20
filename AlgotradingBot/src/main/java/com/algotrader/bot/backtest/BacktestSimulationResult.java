package com.algotrader.bot.backtest;

import java.math.BigDecimal;
import java.util.List;

public record BacktestSimulationResult(
    BigDecimal finalBalance,
    BigDecimal sharpeRatio,
    BigDecimal profitFactor,
    BigDecimal winRatePercent,
    BigDecimal maxDrawdownPercent,
    int totalTrades,
    List<BacktestEquityPointSample> equitySeries,
    List<BacktestTradeSample> tradeSeries
) {
}
