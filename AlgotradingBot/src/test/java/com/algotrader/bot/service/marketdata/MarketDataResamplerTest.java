package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketDataResamplerTest {

    private final MarketDataResampler marketDataResampler = new MarketDataResampler();

    @Test
    void resample_aggregatesFifteenMinuteBarsIntoHourlyBucketsPerSymbol() {
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        List<OHLCVData> bars = List.of(
            candle(start, "BTC/USDT", bd("100"), bd("101"), bd("99"), bd("100.5"), bd("10")),
            candle(start.plusMinutes(15), "BTC/USDT", bd("100.5"), bd("102"), bd("100"), bd("101.5"), bd("11")),
            candle(start.plusMinutes(30), "BTC/USDT", bd("101.5"), bd("103"), bd("101"), bd("102.5"), bd("12")),
            candle(start.plusMinutes(45), "BTC/USDT", bd("102.5"), bd("104"), bd("102"), bd("103.5"), bd("13")),
            candle(start, "ETH/USDT", bd("200"), bd("201"), bd("199"), bd("200.5"), bd("20")),
            candle(start.plusMinutes(15), "ETH/USDT", bd("200.5"), bd("202"), bd("200"), bd("201.5"), bd("21")),
            candle(start.plusMinutes(30), "ETH/USDT", bd("201.5"), bd("203"), bd("201"), bd("202.5"), bd("22")),
            candle(start.plusMinutes(45), "ETH/USDT", bd("202.5"), bd("204"), bd("202"), bd("203.5"), bd("23"))
        );

        List<OHLCVData> resampled = marketDataResampler.resample(bars, "1h");

        assertEquals(2, resampled.size());
        assertEquals(new BigDecimal("100"), resampled.get(0).getOpen());
        assertEquals(new BigDecimal("104"), resampled.get(0).getHigh());
        assertEquals(new BigDecimal("99"), resampled.get(0).getLow());
        assertEquals(new BigDecimal("103.5"), resampled.get(0).getClose());
        assertEquals(new BigDecimal("46"), resampled.get(0).getVolume());
        assertEquals(new BigDecimal("200"), resampled.get(1).getOpen());
        assertEquals(new BigDecimal("204"), resampled.get(1).getHigh());
        assertEquals(new BigDecimal("199"), resampled.get(1).getLow());
        assertEquals(new BigDecimal("203.5"), resampled.get(1).getClose());
        assertEquals(new BigDecimal("86"), resampled.get(1).getVolume());
    }

    @Test
    void resample_rejectsFinerRequestedTimeframeThanDatasetGranularity() {
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        List<OHLCVData> hourlyBars = List.of(
            candle(start, "BTC/USDT", bd("100"), bd("101"), bd("99"), bd("100.5"), bd("10")),
            candle(start.plusHours(1), "BTC/USDT", bd("101"), bd("102"), bd("100"), bd("101.5"), bd("11"))
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> marketDataResampler.resample(hourlyBars, "15m")
        );

        assertEquals("Requested timeframe 15m is finer than dataset granularity 1h.", exception.getMessage());
    }

    @Test
    void resample_skipsIncompleteBucketsWhenFinerBarsHaveGaps() {
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        List<OHLCVData> bars = List.of(
            candle(start, "BTC/USDT", bd("100"), bd("101"), bd("99"), bd("100.5"), bd("10")),
            candle(start.plusMinutes(15), "BTC/USDT", bd("100.5"), bd("102"), bd("100"), bd("101.5"), bd("11")),
            candle(start.plusMinutes(30), "BTC/USDT", bd("101.5"), bd("103"), bd("101"), bd("102.5"), bd("12"))
        );

        List<OHLCVData> resampled = marketDataResampler.resample(bars, "1h");

        assertEquals(0, resampled.size());
    }

    private OHLCVData candle(LocalDateTime timestamp,
                             String symbol,
                             BigDecimal open,
                             BigDecimal high,
                             BigDecimal low,
                             BigDecimal close,
                             BigDecimal volume) {
        return new OHLCVData(timestamp, symbol, open, high, low, close, volume);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
