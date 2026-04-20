package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RegimeFilteredMeanReversionBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 200;
    private static final int BOLLINGER_PERIOD = 20;
    private static final int RSI_PERIOD = 3;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int MAX_HOLDING_BARS = 5;
    private static final BigDecimal ADX_RANGE_THRESHOLD = new BigDecimal("20");
    private static final BigDecimal RSI_OVERSOLD_THRESHOLD = new BigDecimal("20");
    private static final BigDecimal BAND_MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.97");
    private static final BigDecimal ENTRY_ALLOCATION = new BigDecimal("0.60");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.5");
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(LONG_TREND_PERIOD, 0, 0, TREND_FLOOR_BUFFER);
    private static final StrategyFeatureLibrary.RegimeClassifierSpec REGIME_SPEC =
        new StrategyFeatureLibrary.RegimeClassifierSpec(LONG_TREND_PERIOD, ADX_PERIOD, ADX_RANGE_THRESHOLD);
    private static final StrategyFeatureLibrary.LongRiskSpec LONG_RISK_SPEC =
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, null);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.REGIME_FILTERED_MEAN_REVERSION,
        "Regime-Filtered Mean Reversion",
        "Trades short-term oversold snapbacks only when trend strength is muted and the market is not breaking down.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public RegimeFilteredMeanReversionBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                       StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public RegimeFilteredMeanReversionBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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
        BigDecimal middleBand = indicatorCalculator.simpleMovingAverage(context.candles(), index, BOLLINGER_PERIOD);
        BigDecimal lowerBand = indicatorCalculator.bollingerLowerBand(
            context.candles(),
            index,
            BOLLINGER_PERIOD,
            BAND_MULTIPLIER
        );
        BigDecimal previousRsi = indicatorCalculator.relativeStrengthIndex(context.candles(), index - 1, RSI_PERIOD);
        BigDecimal previousClose = context.candles().get(index - 1).getClose();
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

        boolean rangeRegime = regime.rangeBound() && trend.aboveFloor();
        boolean oversoldSnapback = previousClose.compareTo(lowerBand) < 0
            && previousRsi.compareTo(RSI_OVERSOLD_THRESHOLD) < 0
            && close.compareTo(previousClose) > 0;

        if (!context.inPosition() && rangeRegime && oversoldSnapback) {
            return BacktestStrategyDecision.buy(context.primarySymbol(), ENTRY_ALLOCATION, "Oversold snapback in range regime");
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                null
            );
            if (close.compareTo(middleBand) >= 0
                || close.compareTo(riskLevels.effectiveStop()) < 0
                || context.holdingBars() >= MAX_HOLDING_BARS) {
                return BacktestStrategyDecision.sell("Mean-reversion target, stop, or time stop reached");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
