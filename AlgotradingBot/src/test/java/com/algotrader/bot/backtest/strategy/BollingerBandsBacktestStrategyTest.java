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

class BollingerBandsBacktestStrategyTest {

    @Test
    void evaluate_buysAfterConfirmedBounceInsideRisingTrend() {
        BollingerBandsBacktestStrategy strategy = new BollingerBandsBacktestStrategy(
            new StubIndicatorCalculator(bd(98), bd(97), bd(95), bd(102), bd(2))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(50, bd(94), bd(96), null));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("BTC/USDT", decision.targetSymbol());
        assertEquals(new BigDecimal("0.75"), decision.allocationFraction());
    }

    @Test
    void evaluate_skipsEntryWhenTrendFloorBreaks() {
        BollingerBandsBacktestStrategy strategy = new BollingerBandsBacktestStrategy(
            new StubIndicatorCalculator(bd(100), bd(99), bd(95), bd(102), bd(2))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(50, bd(94), bd(96), null));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_exitsWhenTimeStopIsReached() {
        BollingerBandsBacktestStrategy strategy = new BollingerBandsBacktestStrategy(
            new StubIndicatorCalculator(bd(98), bd(97), bd(95), bd(110), bd(2))
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "BTC/USDT",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd(100),
            bd(100),
            BigDecimal.ONE,
            38,
            12
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(50, bd(94), bd(96), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              BigDecimal previousClose,
                                              BigDecimal currentClose,
                                              BacktestOpenPosition openPosition) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        for (int index = 0; index < 60; index++) {
            BigDecimal close = bd(100);
            if (index == currentIndex - 1) {
                close = previousClose;
            } else if (index == currentIndex) {
                close = currentClose;
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

        private final BigDecimal currentTrendEma;
        private final BigDecimal previousTrendEma;
        private final BigDecimal previousLowerBand;
        private final BigDecimal middleBand;
        private final BigDecimal atr;

        private StubIndicatorCalculator(BigDecimal currentTrendEma,
                                        BigDecimal previousTrendEma,
                                        BigDecimal previousLowerBand,
                                        BigDecimal middleBand,
                                        BigDecimal atr) {
            this.currentTrendEma = currentTrendEma;
            this.previousTrendEma = previousTrendEma;
            this.previousLowerBand = previousLowerBand;
            this.middleBand = middleBand;
            this.atr = atr;
        }

        @Override
        public BigDecimal simpleMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return middleBand;
        }

        @Override
        public BigDecimal bollingerLowerBand(List<OHLCVData> candles, int endIndex, int period, BigDecimal multiplier) {
            return previousLowerBand;
        }

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return endIndex == 49 ? previousTrendEma : currentTrendEma;
        }

        @Override
        public BigDecimal averageTrueRange(List<OHLCVData> candles, int endIndex, int period) {
            return atr;
        }
    }
}
