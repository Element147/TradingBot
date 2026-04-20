package com.algotrader.bot.service.marketdata;

import java.util.LinkedHashSet;
import java.util.Set;

public record LegacyMarketDataMigrationRequest(
    boolean dryRun,
    Set<Long> datasetIds,
    Integer limit
) {

    public LegacyMarketDataMigrationRequest {
        datasetIds = datasetIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(datasetIds));
    }
}
