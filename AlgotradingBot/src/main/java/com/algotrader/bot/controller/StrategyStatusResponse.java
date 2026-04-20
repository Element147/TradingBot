package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Current status and performance metrics of a trading strategy")
public record StrategyStatusResponse(
    @Schema(description = "Current account value in USDT", example = "1100.00")
    BigDecimal accountValue,
    @Schema(description = "Total profit/loss in USDT", example = "100.00")
    BigDecimal pnl,
    @Schema(description = "Profit/loss as a percentage", example = "10.00")
    BigDecimal pnlPercent,
    @Schema(description = "Sharpe ratio (risk-adjusted return)", example = "1.25")
    BigDecimal sharpeRatio,
    @Schema(description = "Maximum drawdown in USDT", example = "50.00")
    BigDecimal maxDrawdown,
    @Schema(description = "Maximum drawdown as a percentage", example = "5.00")
    BigDecimal maxDrawdownPercent,
    @Schema(description = "Number of currently open positions", example = "2")
    int openPositions,
    @Schema(description = "Total number of trades executed", example = "25")
    int totalTrades,
    @Schema(description = "Win rate as a percentage", example = "52.50")
    BigDecimal winRate,
    @Schema(description = "Profit factor (gross profit / gross loss)", example = "1.75")
    BigDecimal profitFactor,
    @Schema(description = "Account status", example = "ACTIVE", allowableValues = {"ACTIVE", "STOPPED", "CIRCUIT_BREAKER_TRIGGERED"})
    String status
) {}
