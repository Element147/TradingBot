package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestTelemetryPointResponse(
    LocalDateTime timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    Long segmentId,
    BigDecimal exposurePct,
    String regime
) {}
