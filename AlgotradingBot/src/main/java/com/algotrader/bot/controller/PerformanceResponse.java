package com.algotrader.bot.controller;

public record PerformanceResponse(
    String totalProfitLoss,
    String profitLossPercentage,
    String winRate,
    Integer tradeCount,
    String cashRatio
) {}
