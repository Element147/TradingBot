package com.algotrader.bot.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperTradingStateResponse(
    Boolean paperMode,
    BigDecimal cashBalance,
    Integer positionCount,
    Long totalOrders,
    Long openOrders,
    Long filledOrders,
    Long cancelledOrders,
    LocalDateTime lastOrderAt,
    LocalDateTime lastPositionUpdateAt,
    Long staleOpenOrderCount,
    Long stalePositionCount,
    String recoveryStatus,
    String recoveryMessage,
    String incidentSummary,
    java.util.List<PaperTradingAlertResponse> alerts
) {}
