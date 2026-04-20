package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class IchimokuTrendBacktestStrategy implements BacktestStrategy {

    private static final int DISPLACEMENT = 26;
    private static final int SPAN_B_PERIOD = 52;
    private static final int VOLATILITY_LOOKBACK = 20;
    private static final int ATR_PERIOD = 14;
    private static final BigDecimal TARGET_VOLATILITY = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.40");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("2.0");
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.ICHIMOKU_TREND,
        "Ichimoku Trend",
        "Long/cash Ichimoku trend filter that uses historically projected cloud values to avoid look-ahead bias.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        SPAN_B_PERIOD + DISPLACEMENT
    );

    private final BacktestIndicatorCalculator indicatorCalculator;

    public IchimokuTrendBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this.indicatorCalculator = indicatorCalculator;
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

        BigDecimal currentClose = context.currentClose();
        BigDecimal conversionLine = indicatorCalculator.ichimokuConversionLine(context.candles(), index);
        BigDecimal baseLine = indicatorCalculator.ichimokuBaseLine(context.candles(), index);
        BigDecimal cloudA = indicatorCalculator.ichimokuLeadingSpanAAtCurrent(context.candles(), index, DISPLACEMENT);
        BigDecimal cloudB = indicatorCalculator.ichimokuLeadingSpanBAtCurrent(context.candles(), index, DISPLACEMENT);
        BigDecimal cloudTop = cloudA.max(cloudB);
        BigDecimal cloudBottom = cloudA.min(cloudB);
        BigDecimal laggingReferenceClose = context.candles().get(index - DISPLACEMENT).getClose();

        boolean bullishCloud = cloudA.compareTo(cloudB) >= 0;
        boolean trendConfirmed = currentClose.compareTo(cloudTop) > 0
            && conversionLine.compareTo(baseLine) >= 0
            && currentClose.compareTo(laggingReferenceClose) > 0
            && bullishCloud;

        if (!context.inPosition() && trendConfirmed) {
            BigDecimal allocation = indicatorCalculator.volatilityAdjustedAllocation(
                context.candles(),
                index,
                VOLATILITY_LOOKBACK,
                TARGET_VOLATILITY,
                MIN_ALLOCATION
            );
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                allocation,
                "Price cleared the historical cloud with bullish conversion/base alignment"
            );
        }

        if (context.inPosition()) {
            BigDecimal atr = indicatorCalculator.averageTrueRange(context.candles(), index, ATR_PERIOD);
            BigDecimal stopLevel = context.openPosition().entryPrice()
                .subtract(atr.multiply(ATR_STOP_MULTIPLIER));
            boolean trendBroken = currentClose.compareTo(cloudBottom) < 0
                || currentClose.compareTo(baseLine) < 0
                || conversionLine.compareTo(baseLine) < 0;

            if (trendBroken || currentClose.compareTo(stopLevel) < 0) {
                return BacktestStrategyDecision.sell("Ichimoku cloud/base support or ATR stop failed");
            }
        }

        return BacktestStrategyDecision.hold();
    }
}
