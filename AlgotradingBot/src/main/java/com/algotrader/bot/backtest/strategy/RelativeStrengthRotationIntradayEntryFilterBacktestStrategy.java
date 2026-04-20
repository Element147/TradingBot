package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RelativeStrengthRotationIntradayEntryFilterBacktestStrategy implements BacktestStrategy {

    private static final int SHORT_LOOKBACK = 21;
    private static final int LONG_LOOKBACK = 63;
    private static final int ABSOLUTE_FILTER_PERIOD = 200;
    private static final int TREND_EMA_PERIOD = 20;
    private static final int TRIGGER_EMA_PERIOD = 5;
    private static final int BREAKOUT_LOOKBACK = 5;
    private static final int RSI_PERIOD = 5;
    private static final int VOL_LOOKBACK = 20;
    private static final BigDecimal SHORT_WEIGHT = new BigDecimal("0.65");
    private static final BigDecimal LONG_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal TARGET_VOL = new BigDecimal("0.015");
    private static final BigDecimal MIN_ALLOCATION = new BigDecimal("0.35");
    private static final BigDecimal ENTRY_RSI_MIN = new BigDecimal("55");
    private static final BigDecimal EXIT_RSI_MAX = new BigDecimal("45");
    private static final BigDecimal ROTATION_SCORE_BUFFER = new BigDecimal("0.01");
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final Set<String> APPROVED_UNIVERSE = Set.of(
        "SPY",
        "QQQ",
        "VTI",
        "VT",
        "BTC/USDT",
        "ETH/USDT"
    );
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER,
        "Relative Strength Rotation With Intraday Entry Filter",
        "Ranks an approved small liquid universe, requires positive absolute momentum, and only deploys after a fast timing trigger confirms the leader.",
        BacktestStrategySelectionMode.DATASET_UNIVERSE,
        ABSOLUTE_FILTER_PERIOD
    );

    private final BacktestIndicatorCalculator indicatorCalculator;

    public RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(BacktestIndicatorCalculator indicatorCalculator) {
        this.indicatorCalculator = indicatorCalculator;
    }

    @Override
    public BacktestStrategyDefinition definition() {
        return DEFINITION;
    }

    @Override
    public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
        List<String> eligibleSymbols = context.symbols().stream()
            .filter(APPROVED_UNIVERSE::contains)
            .filter(symbol -> context.hasEnoughCandles(symbol, getMinimumCandles()))
            .sorted()
            .toList();

        if (eligibleSymbols.size() < 2) {
            return context.inPosition()
                ? BacktestStrategyDecision.sell("Approved liquid rotation basket unavailable, rotate to cash")
                : BacktestStrategyDecision.hold();
        }

        Optional<CandidateSnapshot> bestCandidate = eligibleSymbols.stream()
            .map(symbol -> buildCandidate(context, symbol))
            .max(Comparator.comparing(CandidateSnapshot::score));

        if (bestCandidate.isEmpty()) {
            return context.inPosition()
                ? BacktestStrategyDecision.sell("No approved leader available, rotate to cash")
                : BacktestStrategyDecision.hold();
        }

        CandidateSnapshot leader = bestCandidate.get();

        if (!context.inPosition()) {
            if (leader.absoluteMomentumPositive() && leader.timingReady()) {
                return BacktestStrategyDecision.buy(
                    leader.symbol(),
                    leader.allocation(),
                    "Approved leader passed absolute momentum and intraday timing confirmation"
                );
            }
            return BacktestStrategyDecision.hold();
        }

        if (!APPROVED_UNIVERSE.contains(context.activeSymbol())) {
            return BacktestStrategyDecision.sell("Active symbol left the approved liquid basket, rotate to cash");
        }

        CandidateSnapshot active = buildCandidate(context, context.activeSymbol());

        if (!active.absoluteMomentumPositive()) {
            return BacktestStrategyDecision.sell("Absolute momentum turned negative, rotate to cash");
        }

        if (active.close().compareTo(active.trendEma()) < 0 && active.rsi().compareTo(EXIT_RSI_MAX) < 0) {
            return BacktestStrategyDecision.sell("Intraday timing support failed, rotate to cash");
        }

        if (!leader.symbol().equalsIgnoreCase(active.symbol())
            && leader.absoluteMomentumPositive()
            && leader.timingReady()
            && leader.score().compareTo(active.score().add(ROTATION_SCORE_BUFFER, MC)) > 0) {
            return BacktestStrategyDecision.rotate(
                leader.symbol(),
                leader.allocation(),
                "Leadership changed and the new approved leader passed the intraday entry filter"
            );
        }

        return BacktestStrategyDecision.hold();
    }

    private CandidateSnapshot buildCandidate(BacktestStrategyContext context, String symbol) {
        List<OHLCVData> candles = context.candles(symbol);
        int index = context.currentIndex(symbol);

        BigDecimal score = relativeMomentumScore(candles, index);
        BigDecimal close = candles.get(index).getClose();
        BigDecimal previousClose = candles.get(index - 1).getClose();
        BigDecimal sma200 = indicatorCalculator.simpleMovingAverage(candles, index, ABSOLUTE_FILTER_PERIOD);
        BigDecimal trendEma = indicatorCalculator.exponentialMovingAverage(candles, index, TREND_EMA_PERIOD);
        BigDecimal triggerEma = indicatorCalculator.exponentialMovingAverage(candles, index, TRIGGER_EMA_PERIOD);
        BigDecimal previousTriggerEma = indicatorCalculator.exponentialMovingAverage(candles, index - 1, TRIGGER_EMA_PERIOD);
        BigDecimal recentHigh = indicatorCalculator.highestHigh(candles, index - 1, BREAKOUT_LOOKBACK);
        BigDecimal rsi = indicatorCalculator.relativeStrengthIndex(candles, index, RSI_PERIOD);
        BigDecimal allocation = indicatorCalculator.volatilityAdjustedAllocation(
            candles,
            index,
            VOL_LOOKBACK,
            TARGET_VOL,
            MIN_ALLOCATION
        );

        BigDecimal longReturn = indicatorCalculator.rollingReturn(candles, index, LONG_LOOKBACK);
        boolean absoluteMomentumPositive = close.compareTo(sma200) >= 0 && longReturn.compareTo(BigDecimal.ZERO) > 0;
        boolean emaReclaim = previousClose.compareTo(previousTriggerEma) <= 0 && close.compareTo(triggerEma) > 0;
        boolean breakoutFollowThrough = previousClose.compareTo(recentHigh) <= 0 && close.compareTo(recentHigh) > 0;
        boolean aboveTrend = close.compareTo(trendEma) >= 0;
        boolean timingReady = absoluteMomentumPositive
            && aboveTrend
            && rsi.compareTo(ENTRY_RSI_MIN) >= 0
            && (emaReclaim || breakoutFollowThrough);

        return new CandidateSnapshot(
            symbol,
            score,
            absoluteMomentumPositive,
            timingReady,
            allocation,
            close,
            trendEma,
            rsi
        );
    }

    private BigDecimal relativeMomentumScore(List<OHLCVData> candles, int index) {
        BigDecimal shortReturn = indicatorCalculator.rollingReturn(candles, index, SHORT_LOOKBACK);
        BigDecimal longReturn = indicatorCalculator.rollingReturn(candles, index, LONG_LOOKBACK);
        return shortReturn.multiply(SHORT_WEIGHT, MC).add(longReturn.multiply(LONG_WEIGHT, MC), MC);
    }

    private record CandidateSnapshot(
        String symbol,
        BigDecimal score,
        boolean absoluteMomentumPositive,
        boolean timingReady,
        BigDecimal allocation,
        BigDecimal close,
        BigDecimal trendEma,
        BigDecimal rsi
    ) {
    }
}
