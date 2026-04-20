package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record BacktestTelemetryProvenanceResponse(
    Long datasetId,
    Long importJobId,
    Long segmentId,
    Long seriesId,
    String providerId,
    String exchangeId,
    String symbol,
    String timeframe,
    String resolutionTier,
    String sourceType,
    LocalDateTime coverageStart,
    LocalDateTime coverageEnd
) {}
