package com.algotrader.bot.controller;

public record BacktestAlgorithmResponse(
    String id,
    String label,
    String description,
    String selectionMode
) {}
