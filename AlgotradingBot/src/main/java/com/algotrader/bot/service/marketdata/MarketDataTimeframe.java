package com.algotrader.bot.service.marketdata;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

public enum MarketDataTimeframe {
    ONE_MINUTE("1m", Duration.ofMinutes(1), Duration.ofHours(12)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5), Duration.ofDays(3)),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15), Duration.ofDays(10)),
    THIRTY_MINUTES("30m", Duration.ofMinutes(30), Duration.ofDays(20)),
    ONE_HOUR("1h", Duration.ofHours(1), Duration.ofDays(40)),
    FOUR_HOURS("4h", Duration.ofHours(4), Duration.ofDays(160)),
    ONE_DAY("1d", Duration.ofDays(1), Duration.ofDays(730));

    private final String id;
    private final Duration step;
    private final Duration chunkWindow;

    MarketDataTimeframe(String id, Duration step, Duration chunkWindow) {
        this.id = id;
        this.step = step;
        this.chunkWindow = chunkWindow;
    }

    public String id() {
        return id;
    }

    public Duration step() {
        return step;
    }

    public Duration chunkWindow() {
        return chunkWindow;
    }

    public boolean isIntraday() {
        return this != ONE_DAY;
    }

    public boolean isFinerThan(MarketDataTimeframe other) {
        return this.step.compareTo(other.step) < 0;
    }

    public boolean isCoarserThan(MarketDataTimeframe other) {
        return this.step.compareTo(other.step) > 0;
    }

    public static MarketDataTimeframe from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Timeframe is required");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(candidate -> candidate.id.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe: " + value));
    }

    public static MarketDataTimeframe fromStep(Duration step) {
        if (step == null || step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("Timeframe step must be positive");
        }
        return Arrays.stream(values())
            .filter(candidate -> candidate.step.equals(step))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe step: " + step));
    }
}
