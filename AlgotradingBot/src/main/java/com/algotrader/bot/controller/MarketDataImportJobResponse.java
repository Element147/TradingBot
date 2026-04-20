package com.algotrader.bot.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MarketDataImportJobResponse(
    Long id,
    String providerId,
    String providerLabel,
    String assetType,
    String datasetName,
    String symbolsCsv,
    String timeframe,
    LocalDate startDate,
    LocalDate endDate,
    boolean adjusted,
    boolean regularSessionOnly,
    String status,
    String statusMessage,
    LocalDateTime nextRetryAt,
    Integer currentSymbolIndex,
    Integer totalSymbols,
    String currentSymbol,
    Integer importedRowCount,
    Long datasetId,
    boolean datasetReady,
    LocalDateTime currentChunkStart,
    Integer attemptCount,
    Integer retryCount,
    Integer maxRetryCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    AsyncTaskMonitorResponse asyncMonitor
) {
}
