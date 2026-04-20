package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;

@Component
public class OpeningRangeVwapBreakoutBacktestStrategy implements BacktestStrategy {

    private static final int LONG_TREND_PERIOD = 50;
    private static final int MEDIUM_TREND_PERIOD = 20;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int VOLUME_AVERAGE_PERIOD = 20;
    private static final int OPENING_RANGE_BARS = 4;
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.995");
    private static final BigDecimal RANGE_ADX_THRESHOLD = new BigDecimal("18");
    private static final BigDecimal MIN_VOLUME_RATIO = new BigDecimal("1.20");
    private static final BigDecimal TARGET_VOLATILITY = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.35");
    private static final BigDecimal MAX_ENTRY_ALLOCATION = new BigDecimal("0.60");
    private static final BigDecimal MAXIMUM_ATR_PERCENT = new BigDecimal("3.50");
    private static final BigDecimal BREAKOUT_BUFFER_MULTIPLIER = new BigDecimal("0.10");
    private static final BigDecimal ATR_STOP_MULTIPLIER = new BigDecimal("1.20");
    private static final LocalTime LAST_ENTRY_TIME = LocalTime.of(15, 15);
    private static final LocalTime SESSION_CUTOFF_TIME = LocalTime.of(15, 45);
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(
            LONG_TREND_PERIOD,
            MEDIUM_TREND_PERIOD,
            0,
            TREND_FLOOR_BUFFER
        );
    private static final StrategyFeatureLibrary.RegimeClassifierSpec REGIME_SPEC =
        new StrategyFeatureLibrary.RegimeClassifierSpec(LONG_TREND_PERIOD, ADX_PERIOD, RANGE_ADX_THRESHOLD);
    private static final StrategyFeatureLibrary.VolumeConfirmationSpec VOLUME_CONFIRMATION_SPEC =
        new StrategyFeatureLibrary.VolumeConfirmationSpec(VOLUME_AVERAGE_PERIOD, MIN_VOLUME_RATIO);
    private static final StrategyFeatureLibrary.VolatilityFilterSpec VOLATILITY_FILTER_SPEC =
        new StrategyFeatureLibrary.VolatilityFilterSpec(
            ATR_PERIOD,
            VOLUME_AVERAGE_PERIOD,
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
        new StrategyFeatureLibrary.SessionAnchorSpec(OPENING_RANGE_BARS, SESSION_CUTOFF_TIME);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.OPENING_RANGE_VWAP_BREAKOUT,
        "Opening Range VWAP Breakout",
        "Buys same-session breakouts only when the opening range, VWAP, volume, and higher-timeframe bias agree.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD + 1
    );

    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public OpeningRangeVwapBreakoutBacktestStrategy(StrategyFeatureLibrary strategyFeatureLibrary) {
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public OpeningRangeVwapBreakoutBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this(new StrategyFeatureLibrary(indicatorCalculator));
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
            return BacktestStrategyDecision.sell("Session cutoff reached; flattening day-trade exposure");
        }

        if (session.barsSinceOpen() <= OPENING_RANGE_BARS || session.afterCutoff()) {
            return BacktestStrategyDecision.hold();
        }

        BigDecimal close = context.currentClose();
        BigDecimal previousClose = context.candles().get(index - 1).getClose();
        LocalTime currentTime = context.candles().get(index).getTimestamp().toLocalTime();
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
        StrategyFeatureLibrary.VolumeConfirmationSnapshot volume = strategyFeatureLibrary.volumeConfirmation(
            context.candles(),
            index,
            VOLUME_CONFIRMATION_SPEC
        );
        StrategyFeatureLibrary.VolatilityFilterSnapshot volatility = strategyFeatureLibrary.volatilityFilter(
            context.candles(),
            index,
            VOLATILITY_FILTER_SPEC
        );
        BigDecimal breakoutBuffer = volatility.atr().multiply(BREAKOUT_BUFFER_MULTIPLIER);
        BigDecimal structuralSupport = session.openingRangeHigh().max(session.sessionVwap());

        boolean bullishTrendBias = trend.aboveFloor()
            && trend.mediumLine().compareTo(trend.longLine()) >= 0
            && trend.mediumTrendUp();
        boolean regimeHealthy = regime.regime() != StrategyFeatureLibrary.StrategyMarketRegime.TREND_DOWN;
        boolean breakoutTriggered = previousClose.compareTo(session.openingRangeHigh()) <= 0
            && close.compareTo(session.openingRangeHigh()) > 0
            && close.subtract(session.openingRangeHigh()).compareTo(breakoutBuffer) >= 0;
        boolean vwapAligned = close.compareTo(session.sessionVwap()) > 0;
        boolean entryWindowOpen = !currentTime.isAfter(LAST_ENTRY_TIME);
        boolean riskWindowHealthy = volatility.passesAtrCap();

        if (!context.inPosition()
            && entryWindowOpen
            && breakoutTriggered
            && vwapAligned
            && volume.confirmed()
            && bullishTrendBias
            && regimeHealthy
            && riskWindowHealthy) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                volatility.managedAllocation().min(MAX_ENTRY_ALLOCATION),
                "Opening range breakout confirmed by VWAP, volume, and bullish regime"
            );
        }

        if (context.inPosition()) {
            StrategyFeatureLibrary.LongRiskLevels riskLevels = strategyFeatureLibrary.longRiskLevels(
                context.candles(),
                index,
                context.openPosition().entryPrice(),
                LONG_RISK_SPEC,
                structuralSupport
            );
            boolean breakoutFailed = close.compareTo(session.openingRangeHigh()) < 0;
            boolean vwapLost = close.compareTo(session.sessionVwap()) < 0;
            boolean stopHit = close.compareTo(riskLevels.effectiveStop()) < 0;

            if (breakoutFailed || vwapLost || stopHit) {
                return BacktestStrategyDecision.sell("Breakout failed, VWAP was lost, or the protective stop was breached");
            }
        }

        return BacktestStrategyDecision.hold();
    }

    private boolean shouldFlattenForSessionClose(BacktestStrategyContext context,
                                                 StrategyFeatureLibrary.SessionAnchorSnapshot session) {
        return session.afterCutoff() || (session.barsSinceOpen() == 1 && context.holdingBars() > 0);
    }
}
