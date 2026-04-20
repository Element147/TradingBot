package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record BacktestRunResponse(
    Long id,
    String status,
    LocalDateTime submittedAt,
    AsyncTaskMonitorResponse asyncMonitor
) {}
