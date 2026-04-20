package com.algotrader.bot.service.marketdata;

import java.util.List;
import java.util.Set;

public record MarketDataProviderDefinition(
    String id,
    String label,
    String description,
    Set<MarketDataAssetType> supportedAssetTypes,
    List<String> supportedTimeframes,
    boolean apiKeyRequired,
    String apiKeyEnvironmentVariable,
    boolean supportsAdjusted,
    boolean supportsRegularSessionOnly,
    List<String> symbolExamples,
    String docsUrl,
    String signupUrl,
    String accountNotes
) {
}
