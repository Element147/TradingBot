package com.algotrader.bot.service.marketdata;

import java.time.LocalDateTime;

public class MarketDataRetryableException extends RuntimeException {

    private final LocalDateTime retryAt;

    public MarketDataRetryableException(String message, LocalDateTime retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    public MarketDataRetryableException(String message, LocalDateTime retryAt, Throwable cause) {
        super(message, cause);
        this.retryAt = retryAt;
    }

    public LocalDateTime getRetryAt() {
        return retryAt;
    }
}
