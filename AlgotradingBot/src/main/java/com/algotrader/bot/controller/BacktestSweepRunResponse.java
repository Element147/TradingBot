package com.algotrader.bot.controller;

import java.util.List;

public record BacktestSweepRunResponse(
    String experimentKey,
    String experimentName,
    int runCount,
    List<Long> backtestIds
) {}
