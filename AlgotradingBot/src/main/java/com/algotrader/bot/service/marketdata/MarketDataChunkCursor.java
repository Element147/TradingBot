package com.algotrader.bot.service.marketdata;

import java.time.LocalDateTime;

public record MarketDataChunkCursor(
    int symbolIndex,
    LocalDateTime chunkStart
) {
}
