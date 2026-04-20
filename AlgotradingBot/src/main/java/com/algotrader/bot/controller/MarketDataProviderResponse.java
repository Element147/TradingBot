package com.algotrader.bot.controller;

import java.util.List;

public record MarketDataProviderResponse(
    String id,
    String label,
    String description,
    List<String> supportedAssetTypes,
    List<String> supportedTimeframes,
    boolean apiKeyRequired,
    String apiKeyEnvironmentVariable,
    boolean apiKeyConfigured,
    String apiKeyConfiguredSource,
    boolean supportsAdjusted,
    boolean supportsRegularSessionOnly,
    List<String> symbolExamples,
    String docsUrl,
    String signupUrl,
    String accountNotes
) {
}
