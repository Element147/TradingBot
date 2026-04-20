package com.algotrader.bot.service.marketdata;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MarketDataProviderRegistry {

    private final List<MarketDataProvider> providers;

    public MarketDataProviderRegistry(List<MarketDataProvider> providers) {
        this.providers = providers.stream()
            .sorted(Comparator.comparing(provider -> provider.definition().label()))
            .toList();
    }

    public List<MarketDataProvider> all() {
        return providers;
    }

    public MarketDataProvider get(String providerId) {
        return providers.stream()
            .filter(provider -> provider.definition().id().equalsIgnoreCase(providerId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported market data provider: " + providerId));
    }
}
