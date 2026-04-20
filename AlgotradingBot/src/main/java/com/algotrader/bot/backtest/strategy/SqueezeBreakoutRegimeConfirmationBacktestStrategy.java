package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SqueezeBreakoutRegimeConfirmationBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 200;
    private static final int MEDIUM_TREND_PERIOD = 50;
    private static final int BREAKOUT_LOOKBACK = 20;
    private static final int MOMENTUM_PERIOD = 5;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int REALIZED_VOLATILITY_PERIOD = 20;
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.995");
    private static final BigDecimal RANGE_ADX_THRESHOLD = new BigDecimal("18");
    private static final BigDecimal BOLLINGER_MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal MAX_SQUEEZE_WIDTH_PERCENT = new BigDecimal("4.0");
    private static final BigDecimal ADX_CONFIRMATION_THRESHOLD = new BigDecimal("20");
    private static final BigDecimal MIN_BREAKOUT_RETURN = new BigDecimal("0.008");
    private static final BigDecimal TARGET_VOLATILITY = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.25");
    private static final BigDecimal MAX_ALLOCATION = new BigDecimal("0.45");
    private static final BigDecimal MAXIMUM_ATR_PERCENT = new BigDecimal("4.00");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.25");
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(
            LONG_TREND_PERIOD,
            MEDIUM_TREND_PERIOD,
            0,
            TREND_FLOOR_BUFFER
        );
    private static final StrategyFeatureLibrary.RegimeClassifierSpec REGIME_SPEC =
        new StrategyFeatureLibrary.RegimeClassifierSpec(LONG_TREND_PERIOD, ADX_PERIOD, RANGE_ADX_THRESHOLD);
    private static final StrategyFeatureLibrary.VolatilityFilterSpec VOLATILITY_FILTER_SPEC =
        new StrategyFeatureLibrary.VolatilityFilterSpec(
            ATR_PERIOD,
            REALIZED_VOLATILITY_PERIOD,
            TARGET_VOLATILITY,
            MIN_ALLOCATION,
            MAXIMUM_ATR_PERCENT,
            BREAKOUT_LOOKBACK,
            BOLLINGER_MULTIPLIER,
            MAX_SQUEEZE_WIDTH_PERCENT
        );
    private static final StrategyFeatureLibrary.LongRiskSpec LONG_RISK_SPEC =
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, null);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.SQUEEZE_BREAKOUT_REGIME_CONFIRMATION,
        "Squeeze Breakout Regime Confirmation",
        "Buys breakouts from volatility compression only when trend, momentum, and regime confirmation agree.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD + 1
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public SqueezeBreakoutRegimeConfirmationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                             StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public SqueezeBreakoutRegimeConfirmationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this(indicatorCalculator, new StrategyFeatureLibrary(indicatorCalculator));
    }

    @Override
    public BacktestStrategyDefinition definition() {
        return DEFINITION;
    }

    @Override
    public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
        int index = context.currentIndex();
        if (index < getMinimumCandles() - 1) {
            return BacktestStrategyDecision.hold();
        }

        BigDecimal close = context.currentClose();
        BigDecimal previousClose = context.candles().get(index - 1).getClose();
        BigDecimal breakoutLevel = indicatorCalculator.highestHigh(context.candles(), index - 1, BREAKOUT_LOOKBACK);
        BigDecimal momentumReturn = indicatorCalculator.rollingReturn(context.candles(), index, MOMENTUM_PERIOD);
        BigDecimal adx = indicatorCalculator.averageDirectionalIndex(context.candles(), index, ADX_PERIOD);
        StrategyFeatureLibrary.TrendFilterSnapshot trend = strategyFeatureLibrary.trendFilter(
            context.candles(),
            index,
            TREND_FILTER_SPEC
        );
        StrategyFeatureLibrary.RegimeSnapshot regime = strategyFeatureLibrary.classifyRegime(
            context.candles(),
            index,
            REGIME_SPEC
        );
        StrategyFeatureLibrary.VolatilityFilterSnapshot volatility = strategyFeatureLibrary.volatilityFilter(
            context.candles(),
            index,
            VOLATILITY_FILTER_SPEC
        );

        boolean squeezeActive = volatility.squeezeActive();
        boolean higherTimeframeHealthy = trend.aboveFloor()
            && trend.mediumLine().compareTo(trend.longLine()) >= 0
            && trend.mediumTrendUp()
            && regime.regime() != StrategyFeatureLibrary.StrategyMarketRegime.TREND_DOWN;
        boolean breakoutTriggered = previousClose.compareTo(breakoutLevel) <= 0
            && close.compareTo(breakoutLevel) > 0;
        boolean momentumConfirmed = momentumReturn.compareTo(MIN_BREAKOUT_RETURN) >= 0
            && adx.compareTo(ADX_CONFIRMATION_THRESHOLD) >= 0;

        if (!context.inPosition()
            && squeezeActive
            && higherTimeframeHealthy
            && breakoutTriggered
            && momentumConfirmed
            && volatility.passesAtrCap()) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                volatility.managedAllocation().min(MAX_ALLOCATION),
                "Squeeze breakout confirmed by regime, momentum, and volatility expansion"
            );
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                breakoutLevel
            );
            boolean failedExpansion = close.compareTo(breakoutLevel) < 0;
            boolean regimeBroken = regime.regime() == StrategyFeatureLibrary.StrategyMarketRegime.TREND_DOWN
                || !trend.aboveFloor();
            boolean stopHit = close.compareTo(riskLevels.effectiveStop()) < 0;

            if (failedExpansion || regimeBroken || stopHit) {
                return BacktestStrategyDecision.sell("Breakout expansion failed, regime broke, or protective stop was hit");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
