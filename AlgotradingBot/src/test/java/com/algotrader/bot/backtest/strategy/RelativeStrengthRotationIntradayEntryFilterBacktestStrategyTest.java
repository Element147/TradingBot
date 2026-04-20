package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.PositionSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelativeStrengthRotationIntradayEntryFilterBacktestStrategyTest {

    @Test
    void evaluate_buysApprovedLeaderWhenAbsoluteMomentumAndTimingAlign() {
        RelativeStrengthRotationIntradayEntryFilterBacktestStrategy strategy =
            new RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(new StubIndicatorCalculator()
                .withSymbol("SPY", bd("0.12"), true, true, bd("0.42"), bd("100"), bd("99"), bd("61"))
                .withSymbol("QQQ", bd("0.08"), true, false, bd("0.38"), bd("100"), bd("99"), bd("54"))
                .withSymbol("SOL/USDT", bd("0.30"), true, true, bd("0.55"), bd("100"), bd("99"), bd("70")));

        BacktestStrategyDecision decision = strategy.evaluate(context(null));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("SPY", decision.targetSymbol());
        assertEquals(0, bd("0.42").compareTo(decision.allocationFraction()));
    }

    @Test
    void evaluate_holdsWhenLeaderFailsIntradayTimingFilter() {
        RelativeStrengthRotationIntradayEntryFilterBacktestStrategy strategy =
            new RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(new StubIndicatorCalculator()
                .withSymbol("SPY", bd("0.12"), true, false, bd("0.42"), bd("100"), bd("99"), bd("52"))
                .withSymbol("QQQ", bd("0.08"), true, false, bd("0.38"), bd("100"), bd("99"), bd("51")));

        BacktestStrategyDecision decision = strategy.evaluate(context(null));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_rotatesWhenNewLeaderClearsTimingWithScoreBuffer() {
        RelativeStrengthRotationIntradayEntryFilterBacktestStrategy strategy =
            new RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(new StubIndicatorCalculator()
                .withSymbol("SPY", bd("0.06"), true, true, bd("0.35"), bd("100"), bd("99"), bd("57"))
                .withSymbol("QQQ", bd("0.10"), true, true, bd("0.40"), bd("100"), bd("99"), bd("63")));
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("103"),
            bd("103"),
            BigDecimal.ONE,
            200,
            4
        );

        BacktestStrategyDecision decision = strategy.evaluate(context(openPosition));

        assertEquals(BacktestStrategyAction.ROTATE, decision.action());
        assertEquals("QQQ", decision.targetSymbol());
    }

    @Test
    void evaluate_sellsToCashWhenAbsoluteMomentumTurnsNegative() {
        RelativeStrengthRotationIntradayEntryFilterBacktestStrategy strategy =
            new RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(new StubIndicatorCalculator()
                .withSymbol("SPY", bd("0.06"), false, false, bd("0.35"), bd("100"), bd("101"), bd("39"))
                .withSymbol("QQQ", bd("0.05"), false, false, bd("0.38"), bd("100"), bd("101"), bd("41")));
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("103"),
            bd("103"),
            BigDecimal.ONE,
            200,
            4
        );

        BacktestStrategyDecision decision = strategy.evaluate(context(openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext context(BacktestOpenPosition openPosition) {
        Map<String, List<OHLCVData>> candlesBySymbol = new HashMap<>();
        Map<String, Integer> indexBySymbol = new HashMap<>();
        String[] symbols = {"SPY", "QQQ", "SOL/USDT"};

        for (String symbol : symbols) {
            candlesBySymbol.put(symbol, candles(symbol));
            indexBySymbol.put(symbol, 205);
        }

        return new BacktestStrategyContext(candlesBySymbol, indexBySymbol, "SPY", openPosition);
    }

    private List<OHLCVData> candles(String symbol) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 230; index++) {
            BigDecimal close = bd("100");
            candles.add(new OHLCVData(
                start.plusHours(index),
                symbol,
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                bd("100")
            ));
        }

        return candles;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class StubIndicatorCalculator extends BacktestIndicatorCalculator {

        private final Map<String, CandidateValues> bySymbol = new HashMap<>();

        private StubIndicatorCalculator withSymbol(String symbol,
                                                   BigDecimal score,
                                                   boolean absolutePositive,
                                                   boolean timingReady,
                                                   BigDecimal allocation,
                                                   BigDecimal close,
                                                   BigDecimal trendEma,
                                                   BigDecimal rsi) {
            bySymbol.put(symbol, new CandidateValues(score, absolutePositive, timingReady, allocation, close, trendEma, rsi));
            return this;
        }

        @Override
        public BigDecimal rollingReturn(List<OHLCVData> candles, int endIndex, int lookback) {
            CandidateValues values = values(candles);
            if (lookback == 63) {
                return values.absolutePositive ? values.score : values.score.negate();
            }
            if (lookback == 21) {
                return values.score;
            }
            throw new IllegalArgumentException("Unexpected lookback: " + lookback);
        }

        @Override
        public BigDecimal simpleMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            CandidateValues values = values(candles);
            return values.absolutePositive ? values.close.subtract(BigDecimal.ONE) : values.close.add(BigDecimal.ONE);
        }

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            CandidateValues values = values(candles);
            if (period == 20) {
                return values.trendEma;
            }
            if (period == 5) {
                return values.timingReady && endIndex == 204
                    ? values.close.add(BigDecimal.ONE)
                    : values.trendEma;
            }
            throw new IllegalArgumentException("Unexpected EMA period: " + period);
        }

        @Override
        public BigDecimal highestHigh(List<OHLCVData> candles, int endIndex, int lookback) {
            CandidateValues values = values(candles);
            return values.timingReady ? values.close.subtract(new BigDecimal("0.5")) : values.close.add(new BigDecimal("0.5"));
        }

        @Override
        public BigDecimal relativeStrengthIndex(List<OHLCVData> candles, int endIndex, int period) {
            return values(candles).rsi;
        }

        @Override
        public BigDecimal volatilityAdjustedAllocation(List<OHLCVData> candles,
                                                       int endIndex,
                                                       int period,
                                                       BigDecimal targetVolatility,
                                                       BigDecimal minimumAllocation) {
            return values(candles).allocation;
        }

        private CandidateValues values(List<OHLCVData> candles) {
            String symbol = candles.get(0).getSymbol();
            CandidateValues values = bySymbol.get(symbol);
            if (values == null) {
                throw new IllegalArgumentException("Missing stub values for " + symbol);
            }
            return values;
        }
    }

    private record CandidateValues(
        BigDecimal score,
        boolean absolutePositive,
        boolean timingReady,
        BigDecimal allocation,
        BigDecimal close,
        BigDecimal trendEma,
        BigDecimal rsi
    ) {
    }
}
