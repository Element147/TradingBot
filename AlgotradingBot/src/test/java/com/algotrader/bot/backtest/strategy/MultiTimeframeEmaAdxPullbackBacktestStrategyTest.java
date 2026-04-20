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

class MultiTimeframeEmaAdxPullbackBacktestStrategyTest {

    @Test
    void evaluate_buysWhenTrendPullbackAndTriggerAllAlign() {
        MultiTimeframeEmaAdxPullbackBacktestStrategy strategy = new MultiTimeframeEmaAdxPullbackBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("103"), bd("104"), bd("103"), bd("101"), bd("16"), bd("1"), bd("0.40"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(205, bd("101"), bd("104")));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("SPY", decision.targetSymbol());
        assertEquals(0, bd("0.40").compareTo(decision.allocationFraction()));
    }

    @Test
    void evaluate_skipsWhenHigherTimeframeTrendIsMisaligned() {
        MultiTimeframeEmaAdxPullbackBacktestStrategy strategy = new MultiTimeframeEmaAdxPullbackBacktestStrategy(
            new StubIndicatorCalculator(bd("105"), bd("102"), bd("101"), bd("100"), bd("101"), bd("16"), bd("1"), bd("0.40"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(205, bd("101"), bd("103")));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_skipsWhenPullbackDefinitionIsMissing() {
        MultiTimeframeEmaAdxPullbackBacktestStrategy strategy = new MultiTimeframeEmaAdxPullbackBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("104"), bd("101"), bd("100"), bd("99"), bd("20"), bd("1"), bd("0.40"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(205, bd("101"), bd("103")));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_skipsWhenContinuationTriggerIsMissing() {
        MultiTimeframeEmaAdxPullbackBacktestStrategy strategy = new MultiTimeframeEmaAdxPullbackBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("104"), bd("103"), bd("100"), bd("102"), bd("20"), bd("1"), bd("0.40"))
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(205, bd("103"), bd("102")));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_sellsWhenTrendSupportFails() {
        MultiTimeframeEmaAdxPullbackBacktestStrategy strategy = new MultiTimeframeEmaAdxPullbackBacktestStrategy(
            new StubIndicatorCalculator(bd("100"), bd("104"), bd("103"), bd("102"), bd("101"), bd("20"), bd("1"), bd("0.40"))
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("103"),
            bd("103"),
            BigDecimal.ONE,
            200,
            3
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(205, bd("101"), bd("100"), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              BigDecimal previousClose,
                                              BigDecimal currentClose) {
        return contextAt(currentIndex, previousClose, currentClose, null);
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              BigDecimal previousClose,
                                              BigDecimal currentClose,
                                              BacktestOpenPosition openPosition) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 240; index++) {
            BigDecimal close = bd("104");
            if (index == currentIndex - 1) {
                close = previousClose;
            } else if (index == currentIndex) {
                close = currentClose;
            }
            candles.add(new OHLCVData(
                start.plusHours(index),
                "SPY",
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                bd("100")
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

        private final BigDecimal ema200;
        private final BigDecimal ema50;
        private final BigDecimal ema21;
        private final BigDecimal ema8;
        private final BigDecimal previousEma8;
        private final BigDecimal adx;
        private final BigDecimal atr;
        private final BigDecimal allocation;

        private StubIndicatorCalculator(BigDecimal ema200,
                                        BigDecimal ema50,
                                        BigDecimal ema21,
                                        BigDecimal ema8,
                                        BigDecimal previousEma8,
                                        BigDecimal adx,
                                        BigDecimal atr,
                                        BigDecimal allocation) {
            this.ema200 = ema200;
            this.ema50 = ema50;
            this.ema21 = ema21;
            this.ema8 = ema8;
            this.previousEma8 = previousEma8;
            this.adx = adx;
            this.atr = atr;
            this.allocation = allocation;
        }

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return switch (period) {
                case 200 -> ema200;
                case 50 -> ema50;
                case 21 -> ema21;
                case 8 -> endIndex == 204 ? previousEma8 : ema8;
                default -> throw new IllegalArgumentException("Unexpected EMA period: " + period);
            };
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
    }
}
