package com.algotrader.bot.service.marketdata;

public enum MarketDataImportJobStatus {
    QUEUED,
    RUNNING,
    WAITING_RETRY,
    COMPLETED,
    FAILED,
    CANCELLED
}
