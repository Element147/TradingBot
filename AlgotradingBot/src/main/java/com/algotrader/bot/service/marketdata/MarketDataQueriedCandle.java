package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketDataQueriedCandle(
    LocalDateTime timestamp,
    String symbol,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    MarketDataCandleProvenance provenance
) {

    public OHLCVData toOhlcvData() {
        return new OHLCVData(timestamp, symbol, open, high, low, close, volume);
    }
}
