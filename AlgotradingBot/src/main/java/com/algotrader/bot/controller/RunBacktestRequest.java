package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(
    description = "Backtest run request. Provide `symbol` for single-symbol strategies. "
        + "Omit `symbol` for dataset-universe strategies to evaluate the whole dataset universe."
)
public record RunBacktestRequest(
    @Schema(description = "Canonical backtest strategy ID from /api/backtests/algorithms", example = "BUY_AND_HOLD")
    @NotBlank(message = "Algorithm type is required")
    String algorithmType,
    @Schema(description = "Dataset ID from /api/backtests/datasets", example = "7")
    @NotNull(message = "Dataset ID is required")
    @Positive(message = "Dataset ID must be positive")
    Long datasetId,
    @Schema(
        description = "Required for SINGLE_SYMBOL strategies. Optional for DATASET_UNIVERSE strategies; "
            + "omit it to let the engine use the whole dataset universe.",
        example = "BTC/USDT",
        nullable = true
    )
    @Size(max = 20, message = "Symbol length must be <= 20 characters")
    String symbol,
    @Schema(description = "Requested simulation timeframe", example = "1h")
    @NotBlank(message = "Timeframe is required")
    @Size(min = 1, max = 10, message = "Timeframe length must be 1-10 characters")
    String timeframe,
    @Schema(description = "Inclusive backtest start date", example = "2025-01-01")
    @NotNull(message = "Start date is required")
    LocalDate startDate,
    @Schema(description = "Exclusive backtest end date boundary", example = "2025-01-02")
    @NotNull(message = "End date is required")
    LocalDate endDate,
    @Schema(description = "Initial paper balance used by the simulation", example = "1000")
    @NotNull(message = "Initial balance is required")
    @Positive(message = "Initial balance must be positive")
    BigDecimal initialBalance,
    @Schema(description = "Trading fees in basis points", example = "10", defaultValue = "10")
    @NotNull(message = "Fees assumption is required")
    @PositiveOrZero(message = "Fees must be non-negative")
    @Max(value = 200, message = "Fees must be <= 200 bps")
    Integer feesBps,
    @Schema(description = "Execution slippage in basis points", example = "3", defaultValue = "3")
    @NotNull(message = "Slippage assumption is required")
    @PositiveOrZero(message = "Slippage must be non-negative")
    @Max(value = 200, message = "Slippage must be <= 200 bps")
    Integer slippageBps,
    @Schema(description = "Optional experiment label used to group related runs", example = "Q1 Momentum Review")
    @Size(max = 120, message = "Experiment name must be <= 120 characters")
    String experimentName
) {
    public RunBacktestRequest {
        feesBps = feesBps == null ? 10 : feesBps;
        slippageBps = slippageBps == null ? 3 : slippageBps;
    }
}
