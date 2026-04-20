package com.algotrader.bot.service.marketdata;

import java.util.List;

public record LegacyMarketDataReconciliationDatasetResult(
    Long datasetId,
    String datasetName,
    String checksumSha256,
    int legacyRowCount,
    String legacySymbolsCsv,
    int expectedSeriesCount,
    int actualSeriesCount,
    int expectedCandleCount,
    int actualCandleCount,
    String expectedCoverageStart,
    String actualCoverageStart,
    String expectedCoverageEnd,
    String actualCoverageEnd,
    String expectedHashSummary,
    String actualHashSummary,
    String status,
    String rollbackAction,
    List<String> discrepancies
) {

    public LegacyMarketDataReconciliationDatasetResult {
        discrepancies = List.copyOf(discrepancies);
    }

    public boolean failed() {
        return "FAILED".equals(status);
    }

    public String toLogLine() {
        String detail = discrepancies.isEmpty()
            ? "No discrepancies."
            : String.join(" | ", discrepancies);
        return "legacy_market_data_reconciliation"
            + " dataset_id=" + datasetId
            + " status=" + status
            + " checksum=" + checksumSha256
            + " legacy_rows=" + legacyRowCount
            + " expected_series=" + expectedSeriesCount
            + " actual_series=" + actualSeriesCount
            + " expected_candles=" + expectedCandleCount
            + " actual_candles=" + actualCandleCount
            + " expected_hash=" + expectedHashSummary
            + " actual_hash=" + actualHashSummary
            + " rollback=\"" + rollbackAction.replace("\"", "'") + "\""
            + " details=\"" + detail.replace("\"", "'") + "\"";
    }
}
