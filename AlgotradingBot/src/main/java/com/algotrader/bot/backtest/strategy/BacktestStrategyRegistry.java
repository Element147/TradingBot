package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class BacktestStrategyRegistry {

    private final Map<BacktestAlgorithmType, BacktestStrategy> strategies;

    public BacktestStrategyRegistry(List<BacktestStrategy> strategies) {
        EnumMap<BacktestAlgorithmType, BacktestStrategy> indexedStrategies =
            new EnumMap<>(BacktestAlgorithmType.class);

        for (BacktestStrategy strategy : strategies) {
            BacktestStrategy previous = indexedStrategies.put(strategy.getType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate backtest strategy registered for type: " + strategy.getType());
            }
        }

        this.strategies = Map.copyOf(indexedStrategies);
    }

    public BacktestStrategy getStrategy(BacktestAlgorithmType type) {
        BacktestStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No backtest strategy registered for type: " + type);
        }
        return strategy;
    }

    public List<BacktestStrategyDefinition> getDefinitions() {
        return strategies.values().stream()
            .sorted(Comparator.comparingInt(strategy -> strategy.getType().ordinal()))
            .map(BacktestStrategy::definition)
            .toList();
    }
}
