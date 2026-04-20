package com.algotrader.bot.controller;

import java.util.List;

public record BacktestComparisonResponse(
    Long baselineBacktestId,
    List<BacktestComparisonItemResponse> items
) {}
