package com.algotrader.bot.service.marketdata;

import java.time.LocalDateTime;

public record MarketDataProviderFetchRequest(
    MarketDataAssetType assetType,
    String requestedSymbol,
    MarketDataTimeframe timeframe,
    LocalDateTime start,
    LocalDateTime end,
    boolean adjusted,
    boolean regularSessionOnly
) {
}
