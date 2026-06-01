package com.algotrader.bot.controller;

import java.util.List;
import java.util.Map;

public record BacktestAlgorithmResponse(
    String id,
    String label,
    String description,
    String selectionMode,
    Map<String, List<String>> parameterGrid
) {
    public BacktestAlgorithmResponse(
        String id,
        String label,
        String description,
        String selectionMode
    ) {
        this(id, label, description, selectionMode, Map.of());
    }
}

