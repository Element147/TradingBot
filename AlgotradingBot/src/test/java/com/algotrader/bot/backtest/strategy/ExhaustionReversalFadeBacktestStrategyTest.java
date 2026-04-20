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

class ExhaustionReversalFadeBacktestStrategyTest {

    @Test
    void evaluate_buysWhenRangeBoundExhaustionReverses() {
        ExhaustionReversalFadeBacktestStrategy strategy = new ExhaustionReversalFadeBacktestStrategy(
            new StubIndicatorCalculator(
                bd("100"),
                bd("100"),
                bd("16"),
                bd("2"),
                bd("0.30"),
                bd("99.50"),
                bd("28"),
                bd("22"),
                bd("100")
            )
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, candle(bd("98.20"), bd("98.60"), bd("97.80"), bd("98.10"), bd("100")), candle(bd("98.00"), bd("100.40"), bd("97.00"), bd("99.00"), bd("130")), null));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
        assertEquals("SPY", decision.targetSymbol());
        assertEquals(0, bd("0.30").compareTo(decision.allocationFraction()));
    }

    @Test
    void evaluate_skipsStrongDowntrendWithoutClimacticOverride() {
        ExhaustionReversalFadeBacktestStrategy strategy = new ExhaustionReversalFadeBacktestStrategy(
            new StubIndicatorCalculator(
                bd("102"),
                bd("100"),
                bd("30"),
                bd("2"),
                bd("0.30"),
                bd("99.50"),
                bd("24"),
                bd("20"),
                bd("100")
            )
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, candle(bd("98.20"), bd("98.60"), bd("97.80"), bd("98.10"), bd("100")), candle(bd("98.00"), bd("100.40"), bd("97.00"), bd("99.00"), bd("120")), null));

        assertEquals(BacktestStrategyAction.HOLD, decision.action());
    }

    @Test
    void evaluate_buysStrongDowntrendOnlyWhenExhaustionOverrideTriggers() {
        ExhaustionReversalFadeBacktestStrategy strategy = new ExhaustionReversalFadeBacktestStrategy(
            new StubIndicatorCalculator(
                bd("102"),
                bd("100"),
                bd("32"),
                bd("2"),
                bd("0.30"),
                bd("99.50"),
                bd("23"),
                bd("19"),
                bd("100")
            )
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, candle(bd("98.20"), bd("98.60"), bd("97.80"), bd("98.10"), bd("100")), candle(bd("97.80"), bd("100.60"), bd("97.00"), bd("99.00"), bd("220")), null));

        assertEquals(BacktestStrategyAction.BUY, decision.action());
    }

    @Test
    void evaluate_takesProfitWhenMeanReversionTargetIsReached() {
        ExhaustionReversalFadeBacktestStrategy strategy = new ExhaustionReversalFadeBacktestStrategy(
            new StubIndicatorCalculator(
                bd("100"),
                bd("100"),
                bd("18"),
                bd("2"),
                bd("0.30"),
                bd("99.50"),
                bd("35"),
                bd("28"),
                bd("100")
            )
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("98.50"),
            bd("98.50"),
            BigDecimal.ONE,
            53,
            2
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, candle(bd("99.20"), bd("99.60"), bd("98.90"), bd("99.10"), bd("100")), candle(bd("99.40"), bd("100.80"), bd("99.20"), bd("100.20"), bd("130")), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    @Test
    void evaluate_exitsOnTimeStop() {
        ExhaustionReversalFadeBacktestStrategy strategy = new ExhaustionReversalFadeBacktestStrategy(
            new StubIndicatorCalculator(
                bd("100"),
                bd("100"),
                bd("18"),
                bd("2"),
                bd("0.30"),
                bd("99.50"),
                bd("35"),
                bd("28"),
                bd("100")
            )
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("98.50"),
            bd("98.50"),
            BigDecimal.ONE,
            48,
            6
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, candle(bd("98.80"), bd("99.10"), bd("98.50"), bd("98.70"), bd("100")), candle(bd("98.90"), bd("99.20"), bd("98.60"), bd("98.95"), bd("110")), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    @Test
    void evaluate_exitsOnHardStop() {
        ExhaustionReversalFadeBacktestStrategy strategy = new ExhaustionReversalFadeBacktestStrategy(
            new StubIndicatorCalculator(
                bd("100"),
                bd("100"),
                bd("18"),
                bd("2"),
                bd("0.30"),
                bd("99.50"),
                bd("35"),
                bd("28"),
                bd("100")
            )
        );
        BacktestOpenPosition openPosition = new BacktestOpenPosition(
            "SPY",
            PositionSide.LONG,
            BigDecimal.ONE,
            bd("100.00"),
            bd("100.00"),
            BigDecimal.ONE,
            53,
            2
        );

        BacktestStrategyDecision decision = strategy.evaluate(contextAt(55, candle(bd("99.20"), bd("99.60"), bd("98.90"), bd("99.10"), bd("100")), candle(bd("98.40"), bd("98.60"), bd("97.80"), bd("98.20"), bd("120")), openPosition));

        assertEquals(BacktestStrategyAction.SELL, decision.action());
    }

    private BacktestStrategyContext contextAt(int currentIndex,
                                              CandleShape previousShape,
                                              CandleShape currentShape,
                                              BacktestOpenPosition openPosition) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 80; index++) {
            CandleShape shape = candle(bd("100"), bd("100.30"), bd("99.70"), bd("100"), bd("100"));
            if (index == currentIndex - 1) {
                shape = previousShape;
            } else if (index == currentIndex) {
                shape = currentShape;
            }
            candles.add(new OHLCVData(
                start.plusMinutes(index * 15L),
                "SPY",
                shape.open(),
                shape.high(),
                shape.low(),
                shape.close(),
                shape.volume()
            ));
        }

        return new BacktestStrategyContext(
            Map.of("SPY", candles),
            Map.of("SPY", currentIndex),
            "SPY",
            openPosition
        );
    }

    private CandleShape candle(BigDecimal open,
                               BigDecimal high,
                               BigDecimal low,
                               BigDecimal close,
                               BigDecimal volume) {
        return new CandleShape(open, high, low, close, volume);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record CandleShape(
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
    ) {
    }

    private static final class StubIndicatorCalculator extends BacktestIndicatorCalculator {

        private final BigDecimal ema50;
        private final BigDecimal ema20;
        private final BigDecimal adx;
        private final BigDecimal atr;
        private final BigDecimal allocation;
        private final BigDecimal lowerBand;
        private final BigDecimal rsi;
        private final BigDecimal previousRsi;
        private final BigDecimal averageVolume;

        private StubIndicatorCalculator(BigDecimal ema50,
                                        BigDecimal ema20,
                                        BigDecimal adx,
                                        BigDecimal atr,
                                        BigDecimal allocation,
                                        BigDecimal lowerBand,
                                        BigDecimal rsi,
                                        BigDecimal previousRsi,
                                        BigDecimal averageVolume) {
            this.ema50 = ema50;
            this.ema20 = ema20;
            this.adx = adx;
            this.atr = atr;
            this.allocation = allocation;
            this.lowerBand = lowerBand;
            this.rsi = rsi;
            this.previousRsi = previousRsi;
            this.averageVolume = averageVolume;
        }

        @Override
        public BigDecimal exponentialMovingAverage(List<OHLCVData> candles, int endIndex, int period) {
            return switch (period) {
                case 50 -> ema50;
                case 20 -> ema20;
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

        @Override
        public BigDecimal bollingerLowerBand(List<OHLCVData> candles, int endIndex, int period, BigDecimal multiplier) {
            return lowerBand;
        }

        @Override
        public BigDecimal relativeStrengthIndex(List<OHLCVData> candles, int endIndex, int period) {
            return endIndex == 54 ? previousRsi : rsi;
        }

        @Override
        public BigDecimal averageVolume(List<OHLCVData> candles, int endIndex, int period) {
            return averageVolume;
        }
    }
}
