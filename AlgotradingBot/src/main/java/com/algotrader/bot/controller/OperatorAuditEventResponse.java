package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record OperatorAuditEventResponse(
    Long id,
    String actor,
    String action,
    String environment,
    String targetType,
    String targetId,
    String outcome,
    String details,
    LocalDateTime createdAt
) {}
