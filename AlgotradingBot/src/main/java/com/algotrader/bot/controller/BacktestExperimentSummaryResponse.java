package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestExperimentSummaryResponse(
    String experimentKey,
    String experimentName,
    Long latestBacktestId,
    String strategyId,
    String datasetName,
    String symbol,
    String timeframe,
    String latestExecutionStatus,
    String latestValidationStatus,
    Integer runCount,
    LocalDateTime latestRunAt,
    BigDecimal averageReturnPercent,
    BigDecimal bestFinalBalance,
    BigDecimal worstMaxDrawdown
) {}
