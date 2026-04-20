package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record AsyncTaskMonitorResponse(
    String state,
    Integer attemptCount,
    Integer maxAttempts,
    LocalDateTime nextRetryAt,
    Boolean retryEligible,
    Boolean timedOut,
    Long timeoutThresholdSeconds
) {
}
