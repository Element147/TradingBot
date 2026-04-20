package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestStrategyRegistryTest {

    @Test
    void getDefinitions_returnsStrategiesInAlgorithmOrder() {
        BacktestIndicatorCalculator indicatorCalculator = new BacktestIndicatorCalculator();
        BacktestStrategyRegistry registry = new BacktestStrategyRegistry(List.of(
            new BuyAndHoldBacktestStrategy(),
            new DualMomentumRotationBacktestStrategy(indicatorCalculator),
            new VolatilityManagedDonchianBreakoutBacktestStrategy(indicatorCalculator),
            new TrendPullbackContinuationBacktestStrategy(indicatorCalculator),
            new RegimeFilteredMeanReversionBacktestStrategy(indicatorCalculator),
            new TrendFirstAdaptiveEnsembleBacktestStrategy(indicatorCalculator),
            new SmaCrossoverBacktestStrategy(indicatorCalculator),
            new BollingerBandsBacktestStrategy(indicatorCalculator),
            new IchimokuTrendBacktestStrategy(indicatorCalculator),
            new OpeningRangeVwapBreakoutBacktestStrategy(indicatorCalculator),
            new VwapPullbackContinuationBacktestStrategy(indicatorCalculator),
            new ExhaustionReversalFadeBacktestStrategy(indicatorCalculator),
            new MultiTimeframeEmaAdxPullbackBacktestStrategy(indicatorCalculator),
            new SqueezeBreakoutRegimeConfirmationBacktestStrategy(indicatorCalculator),
            new RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(indicatorCalculator)
        ));

        List<BacktestStrategyDefinition> definitions = registry.getDefinitions();

        assertEquals(15, definitions.size());
        assertEquals(BacktestAlgorithmType.BUY_AND_HOLD, definitions.get(0).type());
        assertEquals(BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION, definitions.get(1).type());
        assertEquals(BacktestStrategySelectionMode.DATASET_UNIVERSE, definitions.get(1).selectionMode());
        assertEquals(BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE, definitions.get(5).type());
        assertEquals(BacktestAlgorithmType.BOLLINGER_BANDS, definitions.get(7).type());
        assertEquals(BacktestAlgorithmType.ICHIMOKU_TREND, definitions.get(8).type());
        assertEquals(BacktestAlgorithmType.OPENING_RANGE_VWAP_BREAKOUT, definitions.get(9).type());
        assertEquals(BacktestAlgorithmType.VWAP_PULLBACK_CONTINUATION, definitions.get(10).type());
        assertEquals(BacktestAlgorithmType.EXHAUSTION_REVERSAL_FADE, definitions.get(11).type());
        assertEquals(BacktestAlgorithmType.MULTI_TIMEFRAME_EMA_ADX_PULLBACK, definitions.get(12).type());
        assertEquals(BacktestAlgorithmType.SQUEEZE_BREAKOUT_REGIME_CONFIRMATION, definitions.get(13).type());
        assertEquals(BacktestAlgorithmType.RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER, definitions.get(14).type());
    }

    @Test
    void constructor_rejectsDuplicateStrategies() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            new BacktestStrategyRegistry(List.of(
                new TestStrategy(BacktestAlgorithmType.BUY_AND_HOLD),
                new TestStrategy(BacktestAlgorithmType.BUY_AND_HOLD)
            )));

        assertEquals("Duplicate backtest strategy registered for type: BUY_AND_HOLD", exception.getMessage());
    }

    private record TestStrategy(BacktestAlgorithmType type) implements BacktestStrategy {
        @Override
        public BacktestStrategyDefinition definition() {
            return new BacktestStrategyDefinition(
                type,
                type.name(),
                "test",
                BacktestStrategySelectionMode.SINGLE_SYMBOL,
                1
            );
        }

        @Override
        public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
            return BacktestStrategyDecision.hold();
        }
    }
}
