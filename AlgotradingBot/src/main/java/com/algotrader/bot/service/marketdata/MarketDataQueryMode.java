package com.algotrader.bot.service.marketdata;

public enum MarketDataQueryMode {
    EXACT_ONLY,
    BEST_AVAILABLE,
    EXACT_THEN_ROLLUP;

    public boolean allowsRollup() {
        return this == BEST_AVAILABLE || this == EXACT_THEN_ROLLUP;
    }
}
