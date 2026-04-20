package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.PositionSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IchimokuTrendBacktestStrategyTest {

    @Test
    void evaluate_buysWhenHistoricalCloudTrendAndLaggingFiltersAlign() {
        IchimokuTrendBacktestStrategy strategy = new IchimokuTrendBacktestStrategy(
            new StubIndicatorCalculator(bd(128), bd(124), bd(122), bd(118), bd(0.55), bd(2))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(77, bd(130), bd(100), null));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("BTC/USDT", decision.targetSymbol());
        assertEquals(bd(0.55), decision.allocationFraction());
    }

    @Test
    void evaluate_sellsWhenCloudOrBaseSupportFails() {
        IchimokuTrendBacktestStrategy strategy = new IchimokuTrendBacktestStrategy(
            new StubIndicatorCalculator(bd(118), bd(121), bd(123), bd(124), bd(0.55), bd(2))
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "BTC/USDT",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd(125),
            bd(125),
            BigDecimal.ONE,
            60,
            17
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(77, bd(121), bd(100), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              BigDecimal currentClose,
                                              BigDecimal laggingReferenceClose,
                                              BacktestOpenPosition openPosition) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        for (int index = 0; index < 90; index++) {
            BigDecimal close = bd(110);
            if (index == currentIndex) {
                close = currentClose;
            } else if (index == currentIndex - 26) {
                close = laggingReferenceClose;
            }
            candles.add(new OHLCVData(
                start.plusHours(index),
                "BTC/USDT",
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                bd(1000)
            ));
        }

        return new BacktestStrategyContext(
            Map.of("BTC/USDT", candles),
            Map.of("BTC/USDT", currentIndex),
            "BTC/USDT",
            openPosition
        );
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    private static final class StubIndicatorCalculator extends BacktestIndicatorCalculator {

        private final BigDecimal conversionLine;
        private final BigDecimal baseLine;
        private final BigDecimal cloudA;
        private final BigDecimal cloudB;
        private final BigDecimal allocation;
        private final BigDecimal atr;

        private StubIndicatorCalculator(BigDecimal conversionLine,
                                        BigDecimal baseLine,
                                        BigDecimal cloudA,
                                        BigDecimal cloudB,
                                        BigDecimal allocation,
                                        BigDecimal atr) {
            this.conversionLine = conversionLine;
            this.baseLine = baseLine;
            this.cloudA = cloudA;
            this.cloudB = cloudB;
            this.allocation = allocation;
            this.atr = atr;
        }

        @Override
        public BigDecimal ichimokuConversionLine(List<OHLCVData> candles, int endIndex) {
            return conversionLine;
        }

        @Override
        public BigDecimal ichimokuBaseLine(List<OHLCVData> candles, int endIndex) {
            return baseLine;
        }

        @Override
        public BigDecimal ichimokuLeadingSpanAAtCurrent(List<OHLCVData> candles, int currentIndex, int displacement) {
            return cloudA;
        }

        @Override
        public BigDecimal ichimokuLeadingSpanBAtCurrent(List<OHLCVData> candles, int currentIndex, int displacement) {
            return cloudB;
        }

        @Override
        public BigDecimal volatilityAdjustedAllocation(List<OHLCVData> candles,
                                                       int endIndex,
                                                       int period,
                                                       BigDecimal targetVolatility,
                                                       BigDecimal minimumAllocation) {
            return allocation;
        }

        @Override
        public BigDecimal averageTrueRange(List<OHLCVData> candles, int endIndex, int period) {
            return atr;
        }
    }
}
