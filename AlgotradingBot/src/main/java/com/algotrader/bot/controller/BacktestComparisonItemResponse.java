package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestComparisonItemResponse(
    Long id,
    String strategyId,
    String datasetName,
    String datasetChecksumSha256,
    String datasetSchemaVersion,
    LocalDateTime datasetUploadedAt,
    Boolean datasetArchived,
    String symbol,
    String timeframe,
    String executionStatus,
    String validationStatus,
    Integer feesBps,
    Integer slippageBps,
    LocalDateTime timestamp,
    BigDecimal initialBalance,
    BigDecimal finalBalance,
    BigDecimal totalReturnPercent,
    BigDecimal sharpeRatio,
    BigDecimal profitFactor,
    BigDecimal winRate,
    BigDecimal maxDrawdown,
    Integer totalTrades,
    BigDecimal finalBalanceDelta,
    BigDecimal totalReturnDeltaPercent
) {}
