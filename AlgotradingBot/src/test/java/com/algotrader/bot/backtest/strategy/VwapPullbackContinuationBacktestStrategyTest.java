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

class VwapPullbackContinuationBacktestStrategyTest {

    @Test
    void evaluate_buysWhenPullbackTouchesSupportAndMomentumResumes() {
        VwapPullbackContinuationBacktestStrategy strategy = new VwapPullbackContinuationBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("103"), bd("102"), bd("52"), bd("43"), bd("1"), bd("0.45"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, bd("101"), bd("104"), null, true));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("SPY", decision.targetSymbol());
        assertEquals(0, bd("0.45").compareTo(decision.allocationFraction()));
    }

    @Test
    void evaluate_skipsEntryWhenTrendBiasDisagrees() {
        VwapPullbackContinuationBacktestStrategy strategy = new VwapPullbackContinuationBacktestStrategy(
            new StubIndicatorCalculator(bd("104"), bd("101"), bd("103"), bd("102"), bd("52"), bd("43"), bd("1"), bd("0.45"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, bd("101"), bd("104"), null, true));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_skipsChaseEntriesWithoutPullbackTouch() {
        VwapPullbackContinuationBacktestStrategy strategy = new VwapPullbackContinuationBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("103"), bd("102"), bd("52"), bd("43"), bd("1"), bd("0.45"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, bd("104"), bd("106"), null, false));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_flattensAtSessionCutoff() {
        VwapPullbackContinuationBacktestStrategy strategy = new VwapPullbackContinuationBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("102"), bd("103"), bd("102"), bd("52"), bd("43"), bd("1"), bd("0.45"))
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("104"),
            bd("104"),
            BigDecimal.ONE,
            48,
            12
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(64, bd("103"), bd("105"), openPosition, true));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              BigDecimal previousClose,
                                              BigDecimal currentClose,
                                              BacktestOpenPosition openPosition,
                                              boolean supportTouch) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 80; index++) {
            BigDecimal close = bd("103");
            BigDecimal low = bd("104");
            if (index == currentIndex - 1) {
                close = previousClose;
                low = supportTouch
                    ? previousClose.compareTo(bd("103")) < 0 ? bd("101") : previousClose.subtract(BigDecimal.ONE)
                    : previousClose.max(bd("104"));
            } else if (index == currentIndex) {
                close = currentClose;
                low = supportTouch
                    ? currentClose.compareTo(bd("105")) >= 0 ? bd("102") : bd("101")
                    : currentClose.max(bd("104"));
            }
            candles.add(new OHLCVData(
                start.plusMinutes(index * 15L),
                "SPY",
                close,
                close.add(BigDecimal.ONE),
                low,
                close,
                bd("120")
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
        private final BigDecimal ema5;
        private final BigDecimal previousEma5;
        private final BigDecimal rsi;
        private final BigDecimal previousRsi;
        private final BigDecimal atr;
        private final BigDecimal allocation;

        private StubIndicatorCalculator(BigDecimal ema50,
                                        BigDecimal ema20,
                                        BigDecimal ema5,
                                        BigDecimal previousEma5,
                                        BigDecimal rsi,
                                        BigDecimal previousRsi,
                                        BigDecimal atr,
                                        BigDecimal allocation) {
            this.ema50 = ema50;
            this.ema20 = ema20;
            this.ema5 = ema5;
            this.previousEma5 = previousEma5;
            this.rsi = rsi;
            this.previousRsi = previousRsi;
            this.atr = atr;
            this.allocation = allocation;
        }

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return switch (period) {
                case 50 -> ema50;
                case 20 -> ema20;
                case 5 -> endIndex == 54 ? previousEma5 : ema5;
                default -> throw new IllegalArgumentException("Unexpected EMA period: " + period);
            };
        }

        @Override
        public BigDecimal relativeStrengthIndex(List<OHLCVData> candles, int endIndex, int period) {
            return endIndex == 54 ? previousRsi : rsi;
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
    }
}
