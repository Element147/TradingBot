package com.algotrader.bot.migration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class LegacyMarketDataMigrationRunnerSupport {

    private LegacyMarketDataMigrationRunnerSupport() {
    }

    static Set<Long> parseDatasetIds(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        Set<Long> datasetIds = new LinkedHashSet<>();
        Arrays.stream(rawValue.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(Long::parseLong)
            .forEach(datasetIds::add);
        return datasetIds;
    }

    static Integer parseLimit(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Integer.parseInt(rawValue.trim());
    }
}
