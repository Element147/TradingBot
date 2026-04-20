package com.algotrader.bot.service.marketdata;

import java.time.LocalDateTime;

public record MarketDataQueryGap(
    String symbol,
    String timeframe,
    LocalDateTime bucketStart
) {
}
