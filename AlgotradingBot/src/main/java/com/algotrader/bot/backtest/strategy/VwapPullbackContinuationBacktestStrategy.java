package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;

@Component
public class VwapPullbackContinuationBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 50;
    private static final int PULLBACK_EMA_PERIOD = 20;
    private static final int RESUME_EMA_PERIOD = 5;
    private static final int RSI_PERIOD = 5;
    private static final int ATR_PERIOD = 14;
    private static final int REALIZED_VOLATILITY_PERIOD = 20;
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.995");
    private static final BigDecimal RSI_PULLBACK_THRESHOLD = new BigDecimal("45");
    private static final BigDecimal RSI_RESUME_THRESHOLD = new BigDecimal("50");
    private static final BigDecimal TARGET_VOLATILITY = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.35");
    private static final BigDecimal MAX_ALLOCATION = new BigDecimal("0.55");
    private static final BigDecimal MAXIMUM_ATR_PERCENT = new BigDecimal("3.50");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.10");
    private static final LocalTime LAST_ENTRY_TIME = LocalTime.of(15, 15);
    private static final LocalTime SESSION_CUTOFF_TIME = LocalTime.of(15, 45);
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(
            LONG_TREND_PERIOD,
            PULLBACK_EMA_PERIOD,
            0,
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
    private static final StrategyFeatureLibrary.SessionAnchorSpec SESSION_SPEC =
        new StrategyFeatureLibrary.SessionAnchorSpec(4, SESSION_CUTOFF_TIME);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.VWAP_PULLBACK_CONTINUATION,
        "VWAP Pullback Continuation",
        "Buys same-session pullbacks to VWAP or EMA support only after momentum re-acceleration confirms the trend resume.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD + 1
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public VwapPullbackContinuationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                    StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public VwapPullbackContinuationBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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

        StrategyFeatureLibrary.SessionAnchorSnapshot session = strategyFeatureLibrary.sessionAnchors(
            context.candles(),
            index,
            SESSION_SPEC
        );
        if (context.inPosition() && shouldFlattenForSessionClose(context, session)) {
            return BacktestStrategyDecision.sell("Session cutoff reached; flattening same-day pullback trade");
        }

        if (session.afterCutoff()) {
            return BacktestStrategyDecision.hold();
        }

        BigDecimal close = context.currentClose();
        BigDecimal previousClose = context.candles().get(index - 1).getClose();
        BigDecimal ema5 = indicatorCalculator.exponentialMovingAverage(context.candles(), index, RESUME_EMA_PERIOD);
        BigDecimal previousEma5 = indicatorCalculator.exponentialMovingAverage(context.candles(), index - 1, RESUME_EMA_PERIOD);
        BigDecimal rsi = indicatorCalculator.relativeStrengthIndex(context.candles(), index, RSI_PERIOD);
        BigDecimal previousRsi = indicatorCalculator.relativeStrengthIndex(context.candles(), index - 1, RSI_PERIOD);
        LocalTime currentTime = context.candles().get(index).getTimestamp().toLocalTime();
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
        BigDecimal pullbackSupport = session.sessionVwap().max(trend.mediumLine());
        BigDecimal previousLow = context.candles().get(index - 1).getLow();
        BigDecimal currentLow = context.candles().get(index).getLow();

        boolean bullishTrendBias = trend.aboveFloor()
            && trend.mediumLine().compareTo(trend.longLine()) >= 0
            && trend.mediumTrendUp();
        boolean pullbackTouchedSupport = previousLow.compareTo(pullbackSupport) <= 0
            || currentLow.compareTo(pullbackSupport) <= 0;
        boolean momentumReset = previousRsi.compareTo(RSI_PULLBACK_THRESHOLD) <= 0
            && rsi.compareTo(RSI_RESUME_THRESHOLD) >= 0;
        boolean fastEmaReclaim = previousClose.compareTo(previousEma5) <= 0
            && close.compareTo(ema5) > 0;
        boolean continuationConfirmed = momentumReset || fastEmaReclaim;
        boolean entryWindowOpen = !currentTime.isAfter(LAST_ENTRY_TIME);

        if (!context.inPosition()
            && entryWindowOpen
            && bullishTrendBias
            && pullbackTouchedSupport
            && close.compareTo(session.sessionVwap()) > 0
            && continuationConfirmed
            && volatility.passesAtrCap()) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                volatility.managedAllocation().min(MAX_ALLOCATION),
                "Pullback to VWAP or EMA support resumed with momentum confirmation"
            );
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                pullbackSupport
            );
            boolean vwapLost = close.compareTo(session.sessionVwap()) < 0;
            boolean emaSupportLost = close.compareTo(trend.mediumLine()) < 0;
            boolean stopHit = close.compareTo(riskLevels.effectiveStop()) < 0;

            if (vwapLost || emaSupportLost || stopHit) {
                return BacktestStrategyDecision.sell("VWAP support, EMA support, or the protective stop failed");
            }
        }

        return BacktestStrategyDecision.hold();
    }

    private boolean shouldFlattenForSessionClose(BacktestStrategyContext context,
                                                 StrategyFeatureLibrary.SessionAnchorSnapshot session) {
        return session.afterCutoff() || (session.barsSinceOpen() == 1 && context.holdingBars() > 0);
    }
}
