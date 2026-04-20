package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record RecentTradeResponse(
    Long id,
    String symbol,
    String side,
    String entryPrice,
    String exitPrice,
    String quantity,
    String profitLoss,
    String profitLossPercentage,
    LocalDateTime entryTime,
    LocalDateTime exitTime
) {}
