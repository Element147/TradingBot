package com.algotrader.bot.backtest;

import java.math.BigDecimal;
import java.util.List;

public record BacktestSimulationRequest(
    List<OHLCVData> candles,
    String primarySymbol,
    String timeframe,
    BigDecimal initialBalance,
    Integer feesBps,
    Integer slippageBps
) {
}
