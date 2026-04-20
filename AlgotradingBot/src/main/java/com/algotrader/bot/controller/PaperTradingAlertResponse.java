package com.algotrader.bot.controller;

public record PaperTradingAlertResponse(
    String severity,
    String code,
    String summary,
    String recommendedAction
) {}
