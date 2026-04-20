package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BollingerBandsBacktestStrategy implements BacktestStrategy {

    private static final int PERIOD = 20;
    private static final int TREND_FILTER_PERIOD = 50;
    private static final int ATR_PERIOD = 14;
    private static final int MAX_HOLDING_BARS = 12;
    private static final BigDecimal MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal ENTRY_ALLOCATION = new BigDecimal("0.75");
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.97");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.5");
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(TREND_FILTER_PERIOD, 0, 0, TREND_FLOOR_BUFFER);
    private static final StrategyFeatureLibrary.LongRiskSpec LONG_RISK_SPEC =
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, null);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.BOLLINGER_BANDS,
        "Bollinger Bands",
        "Trend-filtered Bollinger snapback strategy that buys confirmed lower-band reversals only while the medium-term trend is still rising.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        TREND_FILTER_PERIOD + 1
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public BollingerBandsBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                          StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public BollingerBandsBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this(indicatorCalculator, new StrategyFeatureLibrary(indicatorCalculator));
    }

    @Override
    public BacktestStrategyDefinition definition() {
        return DEFINITION;
    }

    @Override
    public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
        int index = context.currentIndex();
        if (index < TREND_FILTER_PERIOD) {
            return BacktestStrategyDecision.hold();
        }

        BigDecimal currentClose = context.currentClose();
        BigDecimal previousClose = context.candles().get(index - 1).getClose();
        BigDecimal middleBand = indicatorCalculator.simpleMovingAverage(context.candles(), index, PERIOD);
        BigDecimal previousLowerBand = indicatorCalculator.bollingerLowerBand(context.candles(), index - 1, PERIOD, MULTIPLIER);
        StrategyFeatureLibrary.TrendFilterSnapshot trend = strategyFeatureLibrary.trendFilter(
            context.candles(),
            index,
            TREND_FILTER_SPEC
        );

        boolean trendIntact = trend.longTrendUp() && trend.aboveFloor();
        boolean confirmedSnapback = previousClose.compareTo(previousLowerBand) < 0
            && currentClose.compareTo(previousClose) > 0;

        if (!context.inPosition() && trendIntact && confirmedSnapback) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                ENTRY_ALLOCATION,
                "Confirmed snapback above lower Bollinger band inside a rising trend"
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

            if (!trendIntact
                || currentClose.compareTo(middleBand) >= 0
                || currentClose.compareTo(riskLevels.effectiveStop()) < 0
                || context.holdingBars() >= MAX_HOLDING_BARS) {
                return BacktestStrategyDecision.sell("Mean-reversion target, trend break, time stop, or ATR stop reached");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
