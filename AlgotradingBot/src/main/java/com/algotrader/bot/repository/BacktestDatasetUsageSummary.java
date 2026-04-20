package com.algotrader.bot.repository;

import java.time.LocalDateTime;

public interface BacktestDatasetUsageSummary {
    Long getDatasetId();

    long getUsageCount();

    LocalDateTime getLastUsedAt();
}
