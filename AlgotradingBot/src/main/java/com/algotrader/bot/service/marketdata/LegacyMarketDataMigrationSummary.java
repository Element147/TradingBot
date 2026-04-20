package com.algotrader.bot.service.marketdata;

import java.util.List;

public record LegacyMarketDataMigrationSummary(
    boolean dryRun,
    List<LegacyMarketDataMigrationDatasetResult> datasetResults
) {

    public LegacyMarketDataMigrationSummary {
        datasetResults = List.copyOf(datasetResults);
    }

    public int migratedDatasets() {
        return (int) datasetResults.stream().filter(result -> "MIGRATED".equals(result.status())).count();
    }

    public int failedDatasets() {
        return (int) datasetResults.stream().filter(LegacyMarketDataMigrationDatasetResult::failed).count();
    }

    public int skippedDatasets() {
        return datasetResults.size() - migratedDatasets() - failedDatasets();
    }

    public int insertedCandles() {
        return datasetResults.stream().mapToInt(LegacyMarketDataMigrationDatasetResult::insertedCandleCount).sum();
    }

    public int rejectedRows() {
        return datasetResults.stream().mapToInt(LegacyMarketDataMigrationDatasetResult::rejectedRowCount).sum();
    }

    public String renderReport() {
        return "legacy_market_data_migration_summary"
            + " dry_run=" + dryRun
            + " datasets=" + datasetResults.size()
            + " migrated=" + migratedDatasets()
            + " skipped=" + skippedDatasets()
            + " failed=" + failedDatasets()
            + " inserted_candles=" + insertedCandles()
            + " rejected_rows=" + rejectedRows();
    }
}
