package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record OperatorAuditSummaryResponse(
    Integer visibleEventCount,
    Long totalMatchingEvents,
    Integer successCount,
    Integer failedCount,
    Integer uniqueActors,
    Integer uniqueActions,
    Integer testEventCount,
    Integer paperEventCount,
    Integer liveEventCount,
    LocalDateTime latestEventAt
) {}
