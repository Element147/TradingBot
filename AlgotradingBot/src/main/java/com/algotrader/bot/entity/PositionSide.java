package com.algotrader.bot.entity;

public enum PositionSide {
    LONG,
    SHORT;

    public boolean isLong() {
        return this == LONG;
    }

    public boolean isShort() {
        return this == SHORT;
    }
}
