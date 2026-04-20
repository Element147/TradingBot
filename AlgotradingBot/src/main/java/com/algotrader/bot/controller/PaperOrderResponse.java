package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperOrderResponse(
    Long id,
    String symbol,
    String side,
    String status,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal fillPrice,
    BigDecimal fees,
    BigDecimal slippage,
    LocalDateTime createdAt
) {}
