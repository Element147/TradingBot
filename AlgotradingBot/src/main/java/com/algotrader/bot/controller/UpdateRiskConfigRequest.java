package com.algotrader.bot.controller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateRiskConfigRequest(
    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "0.05")
    BigDecimal maxRiskPerTrade,
    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "0.10")
    BigDecimal maxDailyLossLimit,
    @NotNull
    @DecimalMin(value = "0.10")
    @DecimalMax(value = "0.50")
    BigDecimal maxDrawdownLimit,
    @NotNull
    @Min(1)
    @Max(10)
    Integer maxOpenPositions,
    @NotNull
    @DecimalMin(value = "0.10")
    @DecimalMax(value = "1.00")
    BigDecimal correlationLimit
) {}
