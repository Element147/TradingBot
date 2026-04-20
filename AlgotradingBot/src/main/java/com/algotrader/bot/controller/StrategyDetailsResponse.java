package com.algotrader.bot.controller;

import java.math.BigDecimal;

public record StrategyDetailsResponse(
    Long id,
    String name,
    String type,
    String status,
    String symbol,
    String timeframe,
    BigDecimal riskPerTrade,
    BigDecimal minPositionSize,
    BigDecimal maxPositionSize,
    BigDecimal profitLoss,
    Integer tradeCount,
    BigDecimal currentDrawdown,
    Boolean paperMode,
    Boolean shortSellingEnabled,
    Integer configVersion,
    java.time.LocalDateTime lastConfigChangedAt
) {}
