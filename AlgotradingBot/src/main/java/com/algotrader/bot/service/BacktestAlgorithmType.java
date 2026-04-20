package com.algotrader.bot.service;

import java.util.Locale;

public enum BacktestAlgorithmType {
    BUY_AND_HOLD,
    DUAL_MOMENTUM_ROTATION,
    VOLATILITY_MANAGED_DONCHIAN_BREAKOUT,
    TREND_PULLBACK_CONTINUATION,
    REGIME_FILTERED_MEAN_REVERSION,
    TREND_FIRST_ADAPTIVE_ENSEMBLE,
    SMA_CROSSOVER,
    BOLLINGER_BANDS,
    ICHIMOKU_TREND,
    OPENING_RANGE_VWAP_BREAKOUT,
    VWAP_PULLBACK_CONTINUATION,
    EXHAUSTION_REVERSAL_FADE,
    MULTI_TIMEFRAME_EMA_ADX_PULLBACK,
    SQUEEZE_BREAKOUT_REGIME_CONFIRMATION,
    RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER;

    public static BacktestAlgorithmType from(String value) {
        try {
            String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
            return BacktestAlgorithmType.valueOf(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported algorithm type: " + value);
        }
    }
}
