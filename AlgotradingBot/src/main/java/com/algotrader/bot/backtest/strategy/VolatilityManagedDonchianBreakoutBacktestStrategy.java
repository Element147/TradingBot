package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class VolatilityManagedDonchianBreakoutBacktestStrategy implements BacktestStrategy {

    private static final int TREND_PERIOD = 200;
    private static final int BREAKOUT_PERIOD = 55;
    private static final int EXIT_PERIOD = 20;
    private static final int ATR_PERIOD = 20;
    private static final BigDecimal TARGET_VOL = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.35");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("2");
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(TREND_PERIOD, 0, 0, BigDecimal.ONE);
    private static final StrategyFeatureLibrary.VolatilityFilterSpec VOLATILITY_FILTER_SPEC =
        new StrategyFeatureLibrary.VolatilityFilterSpec(
            ATR_PERIOD,
            ATR_PERIOD,
            TARGET_VOL,
            MIN_ALLOCATION,
            null,
            0,
            null,
            null
        );
    private static final StrategyFeatureLibrary.LongRiskSpec LONG_RISK_SPEC =
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, null);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT,
        "Volatility-Managed Donchian Breakout",
        "Trend-following breakout system with 55-bar entries, 20-bar exits, and volatility-managed position sizing.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        TREND_PERIOD
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public VolatilityManagedDonchianBreakoutBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                             StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public VolatilityManagedDonchianBreakoutBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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
        BigDecimal breakoutLevel = indicatorCalculator.highestHigh(context.candles(), index - 1, BREAKOUT_PERIOD);
        BigDecimal exitLevel = indicatorCalculator.lowestLow(context.candles(), index - 1, EXIT_PERIOD);
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

        if (!context.inPosition() && trend.aboveLongLine() && close.compareTo(breakoutLevel) > 0) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                volatility.managedAllocation(),
                "55-bar breakout above 200 EMA"
            );
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                null
            );
            if (close.compareTo(exitLevel) < 0 || close.compareTo(riskLevels.effectiveStop()) < 0 || !trend.aboveLongLine()) {
                return BacktestStrategyDecision.sell("Breakout failed below trailing Donchian/ATR protection");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
