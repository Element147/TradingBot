package com.algotrader.bot.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DTO representing OHLCV (Open, High, Low, Close, Volume) candlestick data.
 * Used for backtesting with historical market data.
 * All price values use BigDecimal for precision.
 */
public class OHLCVData {

    private final LocalDateTime timestamp;
    private final String symbol;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final BigDecimal volume;

    public OHLCVData(LocalDateTime timestamp, String symbol, BigDecimal open,
                     BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    @Override
    public String toString() {
        return "OHLCVData{" +
                "timestamp=" + timestamp +
                ", symbol='" + symbol + '\'' +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OHLCVData that = (OHLCVData) o;
        return Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(symbol, that.symbol) &&
                Objects.equals(open, that.open) &&
                Objects.equals(high, that.high) &&
                Objects.equals(low, that.low) &&
                Objects.equals(close, that.close) &&
                Objects.equals(volume, that.volume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, symbol, open, high, low, close, volume);
    }
}
