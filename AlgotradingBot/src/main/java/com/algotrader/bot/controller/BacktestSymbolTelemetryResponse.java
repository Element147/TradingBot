package com.algotrader.bot.controller;

import java.util.List;

public record BacktestSymbolTelemetryResponse(
    String symbol,
    List<BacktestTelemetryPointResponse> points,
    List<BacktestActionMarkerResponse> actions,
    List<BacktestIndicatorSeriesResponse> indicators,
    List<BacktestTelemetryProvenanceResponse> provenance
) {}
