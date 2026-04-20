package com.algotrader.bot.service.marketdata;

public record LegacyMarketDataMigrationDatasetResult(
    Long datasetId,
    String datasetName,
    String checksumSha256,
    int rowCount,
    String symbolsCsv,
    int migratedSeriesCount,
    int migratedSegmentCount,
    int insertedCandleCount,
    int duplicateCandleCount,
    int rejectedRowCount,
    String status,
    String message
) {

    public boolean failed() {
        return "FAILED".equals(status);
    }

    public String toLogLine() {
        return "legacy_market_data_migration"
            + " dataset_id=" + datasetId
            + " status=" + status
            + " checksum=" + checksumSha256
            + " rows=" + rowCount
            + " symbols=" + symbolsCsv
            + " migrated_series=" + migratedSeriesCount
            + " migrated_segments=" + migratedSegmentCount
            + " inserted_candles=" + insertedCandleCount
            + " duplicate_candles=" + duplicateCandleCount
            + " rejected_rows=" + rejectedRowCount
            + " message=\"" + message.replace("\"", "'") + "\"";
    }
}
