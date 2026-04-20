package com.algotrader.bot.service.marketdata;

import java.time.LocalDateTime;

public record MarketDataCandleProvenance(
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
) {

    public static MarketDataCandleProvenance legacy(Long datasetId, String symbol, String timeframe) {
        return new MarketDataCandleProvenance(
            datasetId,
            null,
            null,
            null,
            "legacy-csv",
            null,
            symbol,
            timeframe,
            "LEGACY_FALLBACK",
            "LEGACY_CSV",
            null,
            null
        );
    }

    public static MarketDataCandleProvenance derivedRollup(MarketDataCandleProvenance source,
                                                           String targetTimeframe,
                                                           LocalDateTime coverageStart,
                                                           LocalDateTime coverageEnd) {
        return new MarketDataCandleProvenance(
            source == null ? null : source.datasetId(),
            source == null ? null : source.importJobId(),
            source == null ? null : source.segmentId(),
            source == null ? null : source.seriesId(),
            source == null ? null : source.providerId(),
            source == null ? null : source.exchangeId(),
            source == null ? null : source.symbol(),
            targetTimeframe,
            "DERIVED_ROLLUP",
            source == null || source.sourceType() == null
                ? "DERIVED_ROLLUP"
                : source.sourceType() + "_ROLLED_UP",
            coverageStart,
            coverageEnd
        );
    }
}
