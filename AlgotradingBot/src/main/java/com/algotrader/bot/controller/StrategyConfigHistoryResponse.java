package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StrategyConfigHistoryResponse(
    Long id,
    Integer versionNumber,
    String changeReason,
    String symbol,
    String timeframe,
    BigDecimal riskPerTrade,
    BigDecimal minPositionSize,
    BigDecimal maxPositionSize,
    String status,
    Boolean paperMode,
    Boolean shortSellingEnabled,
    LocalDateTime changedAt
) {}
