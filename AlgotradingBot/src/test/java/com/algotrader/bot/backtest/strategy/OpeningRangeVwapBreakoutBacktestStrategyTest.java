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

class OpeningRangeVwapBreakoutBacktestStrategyTest {

    @Test
    void evaluate_buysWhenBreakoutVwapAndVolumeAllConfirm() {
        OpeningRangeVwapBreakoutBacktestStrategy strategy = new OpeningRangeVwapBreakoutBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("24"), bd("1"), bd("0.50"), bd("100"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, bd("103"), bd("105"), bd("250"), null));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("SPY", decision.targetSymbol());
        assertEquals(0, bd("0.50").compareTo(decision.allocationFraction()));
    }

    @Test
    void evaluate_skipsFalseBreakoutWhenVolumeConfirmationFails() {
        OpeningRangeVwapBreakoutBacktestStrategy strategy = new OpeningRangeVwapBreakoutBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("24"), bd("1"), bd("0.50"), bd("100"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, bd("103"), bd("105"), bd("80"), null));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_sellsWhenBreakoutFallsBackThroughProtection() {
        OpeningRangeVwapBreakoutBacktestStrategy strategy = new OpeningRangeVwapBreakoutBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("24"), bd("1"), bd("0.50"), bd("100"))
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("106"),
            bd("106"),
            BigDecimal.ONE,
            48,
            8
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, bd("104"), bd("102"), bd("150"), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    @Test
    void evaluate_flattensAfterSessionCutoff() {
        OpeningRangeVwapBreakoutBacktestStrategy strategy = new OpeningRangeVwapBreakoutBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("24"), bd("1"), bd("0.50"), bd("100"))
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("106"),
            bd("106"),
            BigDecimal.ONE,
            50,
            12
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(64, bd("106"), bd("107"), bd("150"), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              BigDecimal previousClose,
                                              BigDecimal currentClose,
                                              BigDecimal currentVolume,
                                              BacktestOpenPosition openPosition) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        List<BigDecimal> openingCloses = List.of(bd("100"), bd("101"), bd("102"), bd("101"));
        for (int index = 0; index < 80; index++) {
            BigDecimal close = bd("102");
            BigDecimal volume = bd("120");
            if (index < openingCloses.size()) {
                close = openingCloses.get(index);
            } else if (index == currentIndex - 1) {
                close = previousClose;
            } else if (index == currentIndex) {
                close = currentClose;
                volume = currentVolume;
            }
            candles.add(new OHLCVData(
                start.plusMinutes(index * 15L),
                "SPY",
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                volume
            ));
        }

        return new BacktestStrategyContext(
            Map.of("SPY", candles),
            Map.of("SPY", currentIndex),
            "SPY",
            openPosition
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class StubIndicatorCalculator extends BacktestIndicatorCalculator {

        private final BigDecimal ema50;
        private final BigDecimal ema20;
        private final BigDecimal adx;
        private final BigDecimal atr;
        private final BigDecimal allocation;
        private final BigDecimal averageVolume;

        private StubIndicatorCalculator(BigDecimal ema50,
                                        BigDecimal ema20,
                                        BigDecimal adx,
                                        BigDecimal atr,
                                        BigDecimal allocation,
                                        BigDecimal averageVolume) {
            this.ema50 = ema50;
            this.ema20 = ema20;
            this.adx = adx;
            this.atr = atr;
            this.allocation = allocation;
            this.averageVolume = averageVolume;
        }

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            if (period == 50) {
                return ema50;
            }
            if (period == 20) {
                return ema20;
            }
            throw new IllegalArgumentException("Unexpected EMA period: " + period);
        }

        @Override
        public BigDecimal averageDirectionalIndex(List<OHLCVData> candles, int endIndex, int period) {
            return adx;
        }

        @Override
        public BigDecimal averageTrueRange(List<OHLCVData> candles, int endIndex, int period) {
            return atr;
        }

        @Override
        public BigDecimal realizedVolatility(List<OHLCVData> candles, int endIndex, int period) {
            return new BigDecimal("0.05");
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
        public BigDecimal averageVolume(List<OHLCVData> candles, int endIndex, int period) {
            return averageVolume;
        }
    }
}
