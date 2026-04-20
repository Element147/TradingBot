package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BacktestHistoryItemResponse(
    Long id,
    String strategyId,
    String datasetName,
    String experimentName,
    String symbol,
    String timeframe,
    String executionStatus,
    String validationStatus,
    Integer feesBps,
    Integer slippageBps,
    LocalDateTime timestamp,
    BigDecimal initialBalance,
    BigDecimal finalBalance,
    String executionStage,
    Integer progressPercent,
    Integer processedCandles,
    Integer totalCandles,
    LocalDateTime currentDataTimestamp,
    String statusMessage,
    LocalDateTime lastProgressAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    AsyncTaskMonitorResponse asyncMonitor
) {}
