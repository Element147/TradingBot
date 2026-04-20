package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.entity.PositionSide;

import java.math.BigDecimal;

public record BacktestOpenPosition(
    String symbol,
    PositionSide side,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal entryValue,
    BigDecimal allocationFraction,
    int entryTimelineIndex,
    int holdingBars
) {
}
