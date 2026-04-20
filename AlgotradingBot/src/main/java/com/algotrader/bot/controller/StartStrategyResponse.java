package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Response after starting a trading strategy")
public record StartStrategyResponse(
    @Schema(description = "Created account ID", example = "1")
    Long accountId,
    @Schema(description = "Account status", example = "ACTIVE")
    String status,
    @Schema(description = "Initial balance in USDT", example = "1000.00")
    BigDecimal initialBalance,
    @Schema(description = "Success message", example = "Trading strategy started successfully")
    String message
) {}
