package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Response after stopping a trading strategy")
public record StopStrategyResponse(
    @Schema(description = "Account ID", example = "1")
    Long accountId,
    @Schema(description = "Account status", example = "STOPPED")
    String status,
    @Schema(description = "Final balance in USDT", example = "1100.00")
    BigDecimal finalBalance,
    @Schema(description = "Total profit/loss in USDT", example = "100.00")
    BigDecimal totalPnl,
    @Schema(description = "Profit/loss as percentage", example = "10.00")
    BigDecimal pnlPercent,
    @Schema(description = "Success message", example = "Trading strategy stopped successfully")
    String message
) {}
