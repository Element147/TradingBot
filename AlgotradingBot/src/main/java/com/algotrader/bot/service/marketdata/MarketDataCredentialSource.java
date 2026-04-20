package com.algotrader.bot.service.marketdata;

public enum MarketDataCredentialSource {
    NOT_REQUIRED,
    DATABASE,
    ENVIRONMENT,
    DATABASE_LOCKED,
    NONE
}
