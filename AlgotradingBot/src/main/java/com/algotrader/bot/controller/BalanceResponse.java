package com.algotrader.bot.controller;

import java.time.LocalDateTime;
import java.util.List;

public record BalanceResponse(
    String total,
    String available,
    String locked,
    List<AssetBalance> assets,
    LocalDateTime lastSync
) {
    public record AssetBalance(
        String symbol,
        String amount,
        String valueUSD
    ) {}
}
