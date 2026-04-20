package com.algotrader.bot.controller;

import java.util.List;

public record BacktestTelemetryQueryResponse(
    String requestedSymbol,
    String resolvedSymbol,
    List<String> availableSymbols,
    BacktestSymbolTelemetryResponse telemetry
) {}
