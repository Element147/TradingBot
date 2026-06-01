package com.algotrader.bot.backtest;

import java.math.BigDecimal;
import java.util.List;

import java.util.Map;

public record BacktestSimulationRequest(
    List<OHLCVData> candles,
    String primarySymbol,
    String timeframe,
    BigDecimal initialBalance,
    Integer feesBps,
    Integer slippageBps,
    Map<String, String> parameters
) {
    public BacktestSimulationRequest(
        List<OHLCVData> candles,
        String primarySymbol,
        String timeframe,
        BigDecimal initialBalance,
        Integer feesBps,
        Integer slippageBps
    ) {
        this(candles, primarySymbol, timeframe, initialBalance, feesBps, slippageBps, Map.of());
    }
}

