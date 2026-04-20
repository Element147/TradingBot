package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestActionMarkerResponse(
    LocalDateTime timestamp,
    String action,
    BigDecimal price,
    String label
) {}
