package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MultiTimeframeEmaAdxPullbackBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 200;
    private static final int MEDIUM_TREND_PERIOD = 50;
    private static final int PULLBACK_EMA_PERIOD = 21;
    private static final int TRIGGER_EMA_PERIOD = 8;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int REALIZED_VOLATILITY_PERIOD = 20;
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.995");
    private static final BigDecimal ADX_CONFIRMATION_THRESHOLD = new BigDecimal("18");
    private static final BigDecimal TARGET_VOLATILITY = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.25");
    private static final BigDecimal MAX_ALLOCATION = new BigDecimal("0.50");
    private static final BigDecimal MAXIMUM_ATR_PERCENT = new BigDecimal("4.00");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.20");
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(
            LONG_TREND_PERIOD,
            MEDIUM_TREND_PERIOD,
            PULLBACK_EMA_PERIOD,
            TREND_FLOOR_BUFFER
        );
    private static final StrategyFeatureLibrary.VolatilityFilterSpec VOLATILITY_FILTER_SPEC =
        new StrategyFeatureLibrary.VolatilityFilterSpec(
            ATR_PERIOD,
            REALIZED_VOLATILITY_PERIOD,
            TARGET_VOLATILITY,
            MIN_ALLOCATION,
            MAXIMUM_ATR_PERCENT,
            0,
            null,
            null
        );
    private static final StrategyFeatureLibrary.LongRiskSpec LONG_RISK_SPEC =
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, null);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.MULTI_TIMEFRAME_EMA_ADX_PULLBACK,
        "Multi-Timeframe EMA ADX Pullback",
        "Buys moderate pullbacks inside a higher-timeframe EMA trend once lower-timeframe continuation and ADX or volatility confirmation agree.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD + 1
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public MultiTimeframeEmaAdxPullbackBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                        StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public MultiTimeframeEmaAdxPullbackBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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
        BigDecimal ema8 = indicatorCalculator.exponentialMovingAverage(context.candles(), index, TRIGGER_EMA_PERIOD);
        BigDecimal previousEma8 = indicatorCalculator.exponentialMovingAverage(context.candles(), index - 1, TRIGGER_EMA_PERIOD);
        BigDecimal adx = indicatorCalculator.averageDirectionalIndex(context.candles(), index, ADX_PERIOD);
        StrategyFeatureLibrary.TrendFilterSnapshot trend = strategyFeatureLibrary.trendFilter(
            context.candles(),
            index,
            TREND_FILTER_SPEC
        );
        StrategyFeatureLibrary.VolatilityFilterSnapshot volatility = strategyFeatureLibrary.volatilityFilter(
            context.candles(),
            index,
            VOLATILITY_FILTER_SPEC
        );

        boolean higherTimeframeAligned = trend.aboveFloor()
            && trend.mediumLine().compareTo(trend.longLine()) >= 0
            && trend.fastLine().compareTo(trend.mediumLine()) >= 0
            && trend.mediumTrendUp()
            && trend.longTrendUp();
        boolean pullbackDefined = close.compareTo(trend.fastLine()) <= 0
            && close.compareTo(trend.mediumLine()) >= 0;
        boolean continuationTriggered = close.compareTo(ema8) > 0
            && previousClose.compareTo(previousEma8) <= 0;
        boolean confirmationHealthy = adx.compareTo(ADX_CONFIRMATION_THRESHOLD) >= 0 || volatility.passesAtrCap();

        if (!context.inPosition()
            && higherTimeframeAligned
            && pullbackDefined
            && continuationTriggered
            && confirmationHealthy) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                volatility.managedAllocation().min(MAX_ALLOCATION),
                "Higher-timeframe EMA trend resumed after a pullback with ADX or volatility confirmation"
            );
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                trend.mediumLine()
            );
            boolean trendBroken = !higherTimeframeAligned || close.compareTo(trend.mediumLine()) < 0;
            boolean stopHit = close.compareTo(riskLevels.effectiveStop()) < 0;

            if (trendBroken || stopHit) {
                return BacktestStrategyDecision.sell("Higher-timeframe trend alignment or pullback risk support failed");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
