package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TrendPullbackContinuationBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 200;
    private static final int MEDIUM_TREND_PERIOD = 50;
    private static final int PULLBACK_EMA_PERIOD = 20;
    private static final int RESUME_EMA_PERIOD = 5;
    private static final int RSI_PERIOD = 5;
    private static final int ATR_PERIOD = 20;
    private static final BigDecimal RSI_PULLBACK_THRESHOLD = new BigDecimal("35");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.5");
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(LONG_TREND_PERIOD, MEDIUM_TREND_PERIOD, PULLBACK_EMA_PERIOD, BigDecimal.ONE);
    private static final StrategyFeatureLibrary.LongRiskSpec LONG_RISK_SPEC =
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, null);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.TREND_PULLBACK_CONTINUATION,
        "Trend Pullback Continuation",
        "Buys controlled pullbacks inside an established uptrend and exits when continuation fails.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public TrendPullbackContinuationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                     StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public TrendPullbackContinuationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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
        StrategyFeatureLibrary.TrendFilterSnapshot trend = strategyFeatureLibrary.trendFilter(
            context.candles(),
            index,
            TREND_FILTER_SPEC
        );
        BigDecimal ema5 = indicatorCalculator.exponentialMovingAverage(context.candles(), index, RESUME_EMA_PERIOD);
        BigDecimal previousClose = context.candles().get(index - 1).getClose();
        BigDecimal rsi = indicatorCalculator.relativeStrengthIndex(context.candles(), index, RSI_PERIOD);

        boolean trendHealthy = trend.aboveLongLine() && trend.mediumLine().compareTo(trend.longLine()) > 0;
        boolean validPullback = rsi.compareTo(RSI_PULLBACK_THRESHOLD) < 0 || close.compareTo(trend.fastLine()) <= 0;
        boolean continuationResumed = close.compareTo(ema5) > 0 && previousClose.compareTo(ema5) <= 0;

        if (!context.inPosition() && trendHealthy && validPullback && continuationResumed) {
            return BacktestStrategyDecision.buy(context.primarySymbol(), BigDecimal.ONE, "Pullback resumed in bullish higher-timeframe trend");
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                trend.fastLine()
            );
            if (!trendHealthy || close.compareTo(riskLevels.effectiveStop()) < 0) {
                return BacktestStrategyDecision.sell("Trend continuation failed after pullback entry");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
