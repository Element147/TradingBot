package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestTradeSeriesItemResponse(
    String symbol,
    String side,
    LocalDateTime entryTime,
    LocalDateTime exitTime,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal quantity,
    BigDecimal entryValue,
    BigDecimal exitValue,
    BigDecimal returnPct
) {
}
