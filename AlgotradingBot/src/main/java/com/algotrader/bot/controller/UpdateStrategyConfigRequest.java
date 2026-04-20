package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Update strategy configuration request")
public record UpdateStrategyConfigRequest(
    @NotBlank(message = "Symbol is required")
    @Size(min = 3, max = 20, message = "Symbol length must be 3-20")
    String symbol,
    @NotBlank(message = "Timeframe is required")
    @Size(min = 1, max = 10, message = "Timeframe length must be 1-10")
    String timeframe,
    @NotNull(message = "Risk per trade is required")
    @DecimalMin(value = "0.01", message = "Risk per trade must be at least 1%")
    @DecimalMax(value = "0.05", message = "Risk per trade cannot exceed 5%")
    BigDecimal riskPerTrade,
    @NotNull(message = "Min position size is required")
    @Positive(message = "Min position size must be positive")
    BigDecimal minPositionSize,
    @NotNull(message = "Max position size is required")
    @Positive(message = "Max position size must be positive")
    BigDecimal maxPositionSize,
    @NotNull(message = "Short-selling setting is required")
    Boolean shortSellingEnabled
) {
    public UpdateStrategyConfigRequest {
        shortSellingEnabled = shortSellingEnabled == null ? Boolean.FALSE : shortSellingEnabled;
    }

    public UpdateStrategyConfigRequest(String symbol,
                                       String timeframe,
                                       BigDecimal riskPerTrade,
                                       BigDecimal minPositionSize,
                                       BigDecimal maxPositionSize) {
        this(symbol, timeframe, riskPerTrade, minPositionSize, maxPositionSize, Boolean.FALSE);
    }
}
