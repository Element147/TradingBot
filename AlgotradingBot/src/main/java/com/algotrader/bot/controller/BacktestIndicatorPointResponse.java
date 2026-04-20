package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestIndicatorPointResponse(
    LocalDateTime timestamp,
    BigDecimal value
) {}
