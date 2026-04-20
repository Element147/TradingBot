package com.algotrader.bot.controller;

import java.math.BigDecimal;

public record RiskStatusResponse(
    BigDecimal currentDrawdown,
    BigDecimal maxDrawdownLimit,
    BigDecimal dailyLoss,
    BigDecimal dailyLossLimit,
    BigDecimal openRiskExposure,
    BigDecimal positionCorrelation,
    Boolean circuitBreakerActive,
    String circuitBreakerReason
) {}
