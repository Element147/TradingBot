package com.algotrader.bot.controller;

public record StrategyActionResponse(
    Long strategyId,
    String status,
    String message
) {}
