package com.algotrader.bot.service.marketdata;

import java.util.List;

public record LegacyMarketDataReconciliationSummary(
    List<LegacyMarketDataReconciliationDatasetResult> datasetResults
) {

    public LegacyMarketDataReconciliationSummary {
        datasetResults = List.copyOf(datasetResults);
    }

    public int reconciledDatasets() {
        return (int) datasetResults.stream().filter(result -> "RECONCILED".equals(result.status())).count();
    }

    public int failedDatasets() {
        return (int) datasetResults.stream().filter(LegacyMarketDataReconciliationDatasetResult::failed).count();
    }

    public String renderReport() {
        return "legacy_market_data_reconciliation_summary"
            + " datasets=" + datasetResults.size()
            + " reconciled=" + reconciledDatasets()
            + " failed=" + failedDatasets();
    }
}
