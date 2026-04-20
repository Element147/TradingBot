package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktestIndicatorCalculatorTest {

    private final BacktestIndicatorCalculator indicatorCalculator = new BacktestIndicatorCalculator();

    @Test
    void ichimokuLeadingSpanAAtCurrent_usesHistoricalSourceIndexRatherThanCurrentBar() {
        List<OHLCVData> candles = createCloudProjectionCandles();

        BigDecimal spanA = indicatorCalculator.ichimokuLeadingSpanAAtCurrent(candles, 77);

        assertEquals(0, new BigDecimal("100").compareTo(spanA));
    }

    @Test
    void ichimokuLeadingSpanBAtCurrent_usesHistoricalSourceIndexRatherThanCurrentBar() {
        List<OHLCVData> candles = createCloudProjectionCandles();

        BigDecimal spanB = indicatorCalculator.ichimokuLeadingSpanBAtCurrent(candles, 77);

        assertEquals(0, new BigDecimal("100").compareTo(spanB));
    }

    private List<OHLCVData> createCloudProjectionCandles() {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 78; index++) {
            BigDecimal close = index < 52 ? new BigDecimal("100") : new BigDecimal("300");
            candles.add(new OHLCVData(
                start.plusHours(index),
                "BTC/USDT",
                close,
                close,
                close,
                close,
                BigDecimal.valueOf(1000)
            ));
        }

        return candles;
    }
}
