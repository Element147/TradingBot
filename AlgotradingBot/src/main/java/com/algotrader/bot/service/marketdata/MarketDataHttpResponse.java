package com.algotrader.bot.service.marketdata;

import java.net.http.HttpHeaders;

public record MarketDataHttpResponse(
    int statusCode,
    String body,
    HttpHeaders headers
) {
}
