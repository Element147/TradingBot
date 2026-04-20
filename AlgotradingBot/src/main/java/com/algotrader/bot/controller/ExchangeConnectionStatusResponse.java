package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record ExchangeConnectionStatusResponse(
    boolean connected,
    String exchange,
    LocalDateTime lastSync,
    String rateLimitUsage,
    String error
) {}
