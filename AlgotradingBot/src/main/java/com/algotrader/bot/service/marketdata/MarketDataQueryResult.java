package com.algotrader.bot.service.marketdata;

import java.util.List;

public record MarketDataQueryResult(
    List<MarketDataQueriedCandle> candles,
    List<MarketDataQueryGap> gaps,
    String sourceTimeframe,
    MarketDataQueryMode queryMode
) {
}
