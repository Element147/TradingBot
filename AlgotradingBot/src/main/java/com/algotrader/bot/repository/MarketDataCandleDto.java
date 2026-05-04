package com.algotrader.bot.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketDataCandleDto(
    LocalDateTime bucketStart,
    String symbolDisplay,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal closePrice,
    BigDecimal volume,
    Long datasetId,
    Long importJobId,
    Long segmentId,
    Long seriesId,
    String providerId,
    String exchangeId,
    String timeframe,
    String resolutionTier,
    String sourceType,
    LocalDateTime coverageStart,
    LocalDateTime coverageEnd
) {}
