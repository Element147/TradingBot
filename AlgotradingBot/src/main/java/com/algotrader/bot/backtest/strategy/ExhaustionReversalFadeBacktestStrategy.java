package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalTime;

@Component
public class ExhaustionReversalFadeBacktestStrategy implements BacktestStrategy {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final int LONG_TREND_PERIOD = 50;
    private static final int MEAN_REVERSION_PERIOD = 20;
    private static final int RSI_PERIOD = 5;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int VOLUME_AVERAGE_PERIOD = 20;
    private static final int MAX_HOLD_BARS = 6;
    private static final BigDecimal BOLLINGER_MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal TREND_FLOOR_BUFFER = new BigDecimal("0.995");
    private static final BigDecimal RANGE_ADX_THRESHOLD = new BigDecimal("18");
    private static final BigDecimal CLIMAX_VOLUME_RATIO = new BigDecimal("1.50");
    private static final BigDecimal TARGET_VOLATILITY = new BigDecimal("0.02");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.20");
    private static final BigDecimal MAX_ALLOCATION = new BigDecimal("0.35");
    private static final BigDecimal MAXIMUM_ATR_PERCENT = new BigDecimal("4.50");
    private static final BigDecimal MIN_EXTENSION_ATR = new BigDecimal("0.80");
    private static final BigDecimal MIN_RANGE_TO_ATR = new BigDecimal("1.10");
    private static final BigDecimal OVERSOLD_RSI_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal EXTREME_RSI_THRESHOLD = new BigDecimal("25");
    private static final BigDecimal PROFIT_TARGET_ATR_MULTIPLIER = BigDecimal.ONE;
    private static final BigDecimal ATR_STOP_MULTIPLIER = BigDecimal.ONE;
    private static final BigDecimal HARD_STOP_MULTIPLIER = new BigDecimal("0.985");
    private static final LocalTime LAST_ENTRY_TIME = LocalTime.of(15, 0);
    private static final LocalTime SESSION_CUTOFF_TIME = LocalTime.of(15, 45);
    private static final StrategyFeatureLibrary.TrendFilterSpec TREND_FILTER_SPEC =
        new StrategyFeatureLibrary.TrendFilterSpec(
            LONG_TREND_PERIOD,
            MEAN_REVERSION_PERIOD,
            0,
            TREND_FLOOR_BUFFER
        );
    private static final StrategyFeatureLibrary.RegimeClassifierSpec REGIME_SPEC =
        new StrategyFeatureLibrary.RegimeClassifierSpec(LONG_TREND_PERIOD, ADX_PERIOD, RANGE_ADX_THRESHOLD);
    private static final StrategyFeatureLibrary.VolumeConfirmationSpec VOLUME_SPEC =
        new StrategyFeatureLibrary.VolumeConfirmationSpec(VOLUME_AVERAGE_PERIOD, CLIMAX_VOLUME_RATIO);
    private static final StrategyFeatureLibrary.VolatilityFilterSpec VOLATILITY_SPEC =
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
        new StrategyFeatureLibrary.LongRiskSpec(ATR_PERIOD, ATR_STOP_MULTIPLIER, HARD_STOP_MULTIPLIER);
    private static final StrategyFeatureLibrary.SessionAnchorSpec SESSION_SPEC =
        new StrategyFeatureLibrary.SessionAnchorSpec(4, SESSION_CUTOFF_TIME);
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.EXHAUSTION_REVERSAL_FADE,
        "Exhaustion Reversal Fade",
        "Fades same-session downside exhaustion only after volatility expansion, price extension, and bullish reversal confirmation.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        LONG_TREND_PERIOD + 1
    );

    private final BacktestIndicatorCalculator indicatorCalculator;
    private final StrategyFeatureLibrary strategyFeatureLibrary;

    @Autowired
    public ExhaustionReversalFadeBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator,
                                                  StrategyFeatureLibrary strategyFeatureLibrary) {
        this.indicatorCalculator = indicatorCalculator;
        this.strategyFeatureLibrary = strategyFeatureLibrary;
    }

    public ExhaustionReversalFadeBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
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
            return BacktestStrategyDecision.sell("Session cutoff reached; flattening same-day reversal trade");
        }

        if (session.barsSinceOpen() <= 4 || session.afterCutoff()) {
            return BacktestStrategyDecision.hold();
        }

        OHLCVData currentCandle = context.candles().get(index);
        OHLCVData previousCandle = context.candles().get(index - 1);
        BigDecimal close = currentCandle.getClose();
        BigDecimal lowerBand = indicatorCalculator.bollingerLowerBand(
            context.candles(),
            index,
            MEAN_REVERSION_PERIOD,
            BOLLINGER_MULTIPLIER
        );
        BigDecimal rsi = indicatorCalculator.relativeStrengthIndex(context.candles(), index, RSI_PERIOD);
        BigDecimal previousRsi = indicatorCalculator.relativeStrengthIndex(context.candles(), index - 1, RSI_PERIOD);
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
            VOLUME_SPEC
        );
        StrategyFeatureLibrary.VolatilityFilterSnapshot volatility = strategyFeatureLibrary.volatilityFilter(
            context.candles(),
            index,
            VOLATILITY_SPEC
        );

        BigDecimal atr = volatility.atr();
        BigDecimal candleRange = currentCandle.getHigh().subtract(currentCandle.getLow(), MC);
        BigDecimal vwapExtension = session.sessionVwap().subtract(close, MC);
        BigDecimal midpoint = currentCandle.getHigh()
            .add(currentCandle.getLow(), MC)
            .divide(TWO, MC);

        boolean priceExtended = close.compareTo(lowerBand) <= 0
            || currentCandle.getLow().compareTo(lowerBand) <= 0
            || vwapExtension.compareTo(atr.multiply(MIN_EXTENSION_ATR, MC)) >= 0;
        boolean volatilityExpanded = candleRange.compareTo(atr.multiply(MIN_RANGE_TO_ATR, MC)) >= 0;
        boolean bullishReversal = close.compareTo(currentCandle.getOpen()) > 0
            && close.compareTo(previousCandle.getClose()) > 0
            && close.compareTo(midpoint) >= 0;
        boolean oversold = rsi.compareTo(OVERSOLD_RSI_THRESHOLD) <= 0
            || previousRsi.compareTo(OVERSOLD_RSI_THRESHOLD) <= 0;
        boolean exhaustionOverride = regime.regime() == StrategyFeatureLibrary.StrategyMarketRegime.TREND_DOWN
            && volume.confirmed()
            && priceExtended
            && volatilityExpanded
            && rsi.compareTo(EXTREME_RSI_THRESHOLD) <= 0
            && bullishReversal;
        boolean environmentAllowed = regime.rangeBound() || trend.aboveFloor() || exhaustionOverride;
        boolean strongTrendBlocked = regime.regime() == StrategyFeatureLibrary.StrategyMarketRegime.TREND_DOWN
            && !exhaustionOverride;
        boolean entryWindowOpen = !currentCandle.getTimestamp().toLocalTime().isAfter(LAST_ENTRY_TIME);

        if (!context.inPosition()
            && entryWindowOpen
            && !strongTrendBlocked
            && environmentAllowed
            && priceExtended
            && volatilityExpanded
            && bullishReversal
            && oversold
            && close.compareTo(session.sessionVwap()) < 0
            && volatility.passesAtrCap()) {
            return BacktestStrategyDecision.buy(
                context.primarySymbol(),
                volatility.managedAllocation().min(MAX_ALLOCATION),
                "Exhaustion fade confirmed by downside extension, oversold reversal, and regime filter"
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
            BigDecimal atrTarget = context.openPosition().entryPrice()
                .add(riskLevels.atr().multiply(PROFIT_TARGET_ATR_MULTIPLIER, MC), MC);
            BigDecimal profitTarget = session.sessionVwap().compareTo(context.openPosition().entryPrice()) > 0
                ? session.sessionVwap().min(atrTarget)
                : atrTarget;

            if (close.compareTo(profitTarget) >= 0) {
                return BacktestStrategyDecision.sell("Profit target reached on exhaustion reversal fade");
            }
            if (context.holdingBars() >= MAX_HOLD_BARS) {
                return BacktestStrategyDecision.sell("Time stop reached; reversal fade did not mean revert fast enough");
            }
            if (close.compareTo(riskLevels.effectiveStop()) <= 0) {
                return BacktestStrategyDecision.sell("Hard stop or ATR stop breached on exhaustion reversal fade");
            }
        }

        return BacktestStrategyDecision.hold();
    }

    private boolean shouldFlattenForSessionClose(BacktestStrategyContext context,
                                                 StrategyFeatureLibrary.SessionAnchorSnapshot session) {
        return session.afterCutoff() || (session.barsSinceOpen() == 1 && context.holdingBars() > 0);
    }
}
