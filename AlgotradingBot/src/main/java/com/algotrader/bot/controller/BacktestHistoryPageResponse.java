package com.algotrader.bot.controller;

import java.util.List;

public record BacktestHistoryPageResponse(
    List<BacktestHistoryItemResponse> items,
    long total,
    int page,
    int pageSize
) {}
