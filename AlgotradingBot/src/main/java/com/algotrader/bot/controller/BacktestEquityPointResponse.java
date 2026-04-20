package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestEquityPointResponse(
    LocalDateTime timestamp,
    BigDecimal equity,
    BigDecimal drawdownPct
) {
}
