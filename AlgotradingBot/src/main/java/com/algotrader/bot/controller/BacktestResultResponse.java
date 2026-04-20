package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Backtest result with comprehensive performance metrics")
public record BacktestResultResponse(
    @Schema(description = "Strategy identifier", example = "bollinger-bands-v1")
    String strategyId,
    @Schema(description = "Trading pair symbol", example = "BTC/USDT")
    String symbol,
    @Schema(description = "Date range of backtest", example = "2022-01-01 to 2024-01-01")
    String dateRange,
    @Schema(description = "Initial balance in USDT", example = "1000.00")
    BigDecimal initialBalance,
    @Schema(description = "Final balance in USDT", example = "1500.00")
    BigDecimal finalBalance,
    @Schema(description = "Total return as percentage", example = "50.00")
    BigDecimal totalReturn,
    @Schema(description = "Annualized return as percentage", example = "25.00")
    BigDecimal annualReturn,
    @Schema(description = "Sharpe ratio (risk-adjusted return)", example = "1.25")
    BigDecimal sharpeRatio,
    @Schema(description = "Calmar ratio (return / max drawdown)", example = "1.80")
    BigDecimal calmarRatio,
    @Schema(description = "Maximum drawdown as percentage", example = "18.50")
    BigDecimal maxDrawdown,
    @Schema(description = "Win rate as percentage", example = "52.50")
    BigDecimal winRate,
    @Schema(description = "Profit factor (gross profit / gross loss)", example = "1.75")
    BigDecimal profitFactor,
    @Schema(description = "Total number of trades", example = "150")
    int totalTrades,
    @Schema(description = "Number of winning sessions", example = "78")
    int winningSessions,
    @Schema(description = "Number of losing sessions", example = "72")
    int losingSessions,
    @Schema(description = "Validation status", example = "PASSED", allowableValues = {"PASSED", "FAILED", "PENDING"})
    String validationStatus
) {}
