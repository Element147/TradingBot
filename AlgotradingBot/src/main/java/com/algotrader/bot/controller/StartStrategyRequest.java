package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Request to start a new trading strategy")
public record StartStrategyRequest(
    @Schema(
        description = "Initial account balance in USDT",
        example = "1000.00",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Initial balance is required")
    @Positive(message = "Initial balance must be positive")
    BigDecimal initialBalance,
    @Schema(
        description = "List of trading pairs to trade",
        example = "[\"BTC/USDT\", \"ETH/USDT\"]",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Trading pairs are required")
    @Size(min = 1, message = "At least one trading pair is required")
    List<String> pairs,
    @Schema(description = "Risk per trade as a decimal (0.02 = 2%)", example = "0.02", defaultValue = "0.02")
    @DecimalMin(value = "0.001", message = "Risk per trade must be at least 0.1%")
    @DecimalMax(value = "0.05", message = "Risk per trade cannot exceed 5%")
    BigDecimal riskPerTrade,
    @Schema(description = "Maximum drawdown limit as a decimal (0.25 = 25%)", example = "0.25", defaultValue = "0.25")
    @DecimalMin(value = "0.05", message = "Max drawdown must be at least 5%")
    @DecimalMax(value = "0.50", message = "Max drawdown cannot exceed 50%")
    BigDecimal maxDrawdown
) {
    public StartStrategyRequest {
        riskPerTrade = riskPerTrade == null ? new BigDecimal("0.02") : riskPerTrade;
        maxDrawdown = maxDrawdown == null ? new BigDecimal("0.25") : maxDrawdown;
    }
}
