package com.algotrader.bot.backtest;

import com.algotrader.bot.backtest.strategy.BacktestOpenPosition;
import com.algotrader.bot.backtest.strategy.BacktestStrategy;
import com.algotrader.bot.backtest.strategy.BacktestStrategyAction;
import com.algotrader.bot.backtest.strategy.BacktestStrategyContext;
import com.algotrader.bot.backtest.strategy.BacktestStrategyDecision;
import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BacktestSimulationEngine {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int POSITION_SCALE = 8;

    private final BacktestStrategyRegistry strategyRegistry;
    private final BacktestSimulationMetricsCalculator metricsCalculator;

    public BacktestSimulationEngine(BacktestStrategyRegistry strategyRegistry,
                                    BacktestSimulationMetricsCalculator metricsCalculator) {
        this.strategyRegistry = strategyRegistry;
        this.metricsCalculator = metricsCalculator;
    }

    public BacktestSimulationResult simulate(BacktestAlgorithmType algorithmType, BacktestSimulationRequest request) {
        return simulate(algorithmType, request, null);
    }

    public BacktestSimulationResult simulate(BacktestAlgorithmType algorithmType,
                                             BacktestSimulationRequest request,
                                             BacktestSimulationProgressListener progressListener) {
        BacktestStrategy strategy = strategyRegistry.getStrategy(algorithmType);
        Map<String, List<OHLCVData>> scopedCandles = scopeCandles(strategy, request);
        List<LocalDateTime> timeline = buildTimeline(strategy, scopedCandles);
        validateCandles(strategy, scopedCandles, timeline);

        BigDecimal costRate = BigDecimal.valueOf(request.feesBps() + request.slippageBps())
            .divide(BigDecimal.valueOf(10000), MC);
        Map<String, Map<LocalDateTime, Integer>> indexByTimestamp = buildIndexByTimestamp(scopedCandles);

        String primarySymbol = resolvePrimarySymbol(strategy, request.primarySymbol(), scopedCandles);
        BigDecimal cash = request.initialBalance();
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal entryValue = BigDecimal.ZERO;
        BigDecimal entryPrice = BigDecimal.ZERO;
        BigDecimal allocationFraction = BigDecimal.ONE;
        String activeSymbol = null;
        PositionSide activeSide = null;
        int entryTimelineIndex = -1;
        LocalDateTime entryTimestamp = null;
        int winningTrades = 0;
        BacktestStrategyDecision pendingDecision = null;

        List<BigDecimal> tradeReturns = new ArrayList<>();
        List<BacktestEquityPointSample> equitySamples = new ArrayList<>();
        List<BacktestTradeSample> tradeSamples = new ArrayList<>();
        equitySamples.add(new BacktestEquityPointSample(timeline.get(0), request.initialBalance(), BigDecimal.ZERO));
        int totalTimelineSteps = timeline.size();
        int progressMilestone = Math.max(1, totalTimelineSteps / 20);

        for (int timelineIndex = 0; timelineIndex < timeline.size(); timelineIndex++) {
            LocalDateTime currentTimestamp = timeline.get(timelineIndex);
            Map<String, Integer> currentIndexBySymbol = buildCurrentIndexMap(indexByTimestamp, currentTimestamp);

            if (pendingDecision != null) {
                BacktestStrategyContext executionContext = new BacktestStrategyContext(
                    scopedCandles,
                    currentIndexBySymbol,
                    primarySymbol,
                    buildOpenPosition(
                        activeSymbol,
                        activeSide,
                        quantity,
                        entryPrice,
                        entryValue,
                        allocationFraction,
                        entryTimelineIndex,
                        timelineIndex
                    )
                );

                if (activeSymbol == null && pendingDecision.action() == BacktestStrategyAction.BUY) {
                    EntrySnapshot entry = enterPosition(
                        pendingDecision.targetSymbol(),
                        PositionSide.LONG,
                        normalizeAllocation(pendingDecision.allocationFraction()),
                        executionContext.currentCandle(pendingDecision.targetSymbol()).getOpen(),
                        cash,
                        costRate
                    );
                    if (entry != null) {
                        cash = entry.remainingCash();
                        quantity = entry.quantity();
                        entryValue = entry.entryValue();
                        entryPrice = entry.entryPrice();
                        allocationFraction = entry.allocationFraction();
                        activeSymbol = entry.symbol();
                        activeSide = PositionSide.LONG;
                        entryTimelineIndex = timelineIndex;
                        entryTimestamp = currentTimestamp;
                    }
                } else if (activeSymbol == null && pendingDecision.action() == BacktestStrategyAction.SHORT) {
                    EntrySnapshot entry = enterPosition(
                        pendingDecision.targetSymbol(),
                        PositionSide.SHORT,
                        normalizeAllocation(pendingDecision.allocationFraction()),
                        executionContext.currentCandle(pendingDecision.targetSymbol()).getOpen(),
                        cash,
                        costRate
                    );
                    if (entry != null) {
                        cash = entry.remainingCash();
                        quantity = entry.quantity();
                        entryValue = entry.entryValue();
                        entryPrice = entry.entryPrice();
                        allocationFraction = entry.allocationFraction();
                        activeSymbol = entry.symbol();
                        activeSide = PositionSide.SHORT;
                        entryTimelineIndex = timelineIndex;
                        entryTimestamp = currentTimestamp;
                    }
                } else if (activeSymbol != null
                    && shouldExitPosition(activeSide, pendingDecision.action())) {
                    ExitSnapshot exit = exitPosition(
                        activeSide,
                        quantity,
                        entryValue,
                        executionContext.currentCandle(activeSymbol).getOpen(),
                        costRate
                    );
                    cash = cash.add(exit.cashReleased(), MC);
                    tradeReturns.add(exit.tradeReturn());
                    tradeSamples.add(createTradeSample(
                        activeSymbol,
                        activeSide,
                        entryTimestamp,
                        currentTimestamp,
                        entryPrice,
                        exit.exitPrice(),
                        quantity,
                        entryValue,
                        exit.cashReleased(),
                        exit.tradeReturn()
                    ));
                    if (exit.tradeReturn().compareTo(BigDecimal.ZERO) > 0) {
                        winningTrades++;
                    }
                    quantity = BigDecimal.ZERO;
                    entryValue = BigDecimal.ZERO;
                    entryPrice = BigDecimal.ZERO;
                    allocationFraction = BigDecimal.ONE;
                    activeSymbol = null;
                    activeSide = null;
                    entryTimelineIndex = -1;
                    entryTimestamp = null;
                } else if (activeSymbol != null
                    && activeSide == PositionSide.LONG
                    && pendingDecision.action() == BacktestStrategyAction.ROTATE
                    && pendingDecision.targetSymbol() != null
                    && !pendingDecision.targetSymbol().equalsIgnoreCase(activeSymbol)) {
                    ExitSnapshot exit = exitPosition(
                        activeSide,
                        quantity,
                        entryValue,
                        executionContext.currentCandle(activeSymbol).getOpen(),
                        costRate
                    );
                    cash = cash.add(exit.cashReleased(), MC);
                    tradeReturns.add(exit.tradeReturn());
                    tradeSamples.add(createTradeSample(
                        activeSymbol,
                        activeSide,
                        entryTimestamp,
                        currentTimestamp,
                        entryPrice,
                        exit.exitPrice(),
                        quantity,
                        entryValue,
                        exit.cashReleased(),
                        exit.tradeReturn()
                    ));
                    if (exit.tradeReturn().compareTo(BigDecimal.ZERO) > 0) {
                        winningTrades++;
                    }

                    EntrySnapshot entry = enterPosition(
                        pendingDecision.targetSymbol(),
                        PositionSide.LONG,
                        normalizeAllocation(pendingDecision.allocationFraction()),
                        executionContext.currentCandle(pendingDecision.targetSymbol()).getOpen(),
                        cash,
                        costRate
                    );
                    quantity = BigDecimal.ZERO;
                    entryValue = BigDecimal.ZERO;
                    entryPrice = BigDecimal.ZERO;
                    allocationFraction = BigDecimal.ONE;
                    activeSymbol = null;
                    activeSide = null;
                    entryTimelineIndex = -1;
                    entryTimestamp = null;

                    if (entry != null) {
                        cash = entry.remainingCash();
                        quantity = entry.quantity();
                        entryValue = entry.entryValue();
                        entryPrice = entry.entryPrice();
                        allocationFraction = entry.allocationFraction();
                        activeSymbol = entry.symbol();
                        activeSide = PositionSide.LONG;
                        entryTimelineIndex = timelineIndex;
                        entryTimestamp = currentTimestamp;
                    }
                }

                pendingDecision = null;
            }

            BacktestOpenPosition openPosition = buildOpenPosition(
                activeSymbol,
                activeSide,
                quantity,
                entryPrice,
                entryValue,
                allocationFraction,
                entryTimelineIndex,
                timelineIndex
            );

            BacktestStrategyContext context = new BacktestStrategyContext(
                scopedCandles,
                currentIndexBySymbol,
                primarySymbol,
                openPosition
            );
            BacktestStrategyDecision decision = strategy.evaluate(context);

            if (timelineIndex < timeline.size() - 1) {
                pendingDecision = queueNextBarDecision(activeSymbol, activeSide, decision);
            }

            BigDecimal equity = activeSymbol == null
                ? cash
                : markToMarketEquity(activeSide, cash, quantity, entryPrice, entryValue, context.currentClose(activeSymbol));
            equitySamples.add(new BacktestEquityPointSample(currentTimestamp, equity, BigDecimal.ZERO));

            if (progressListener != null) {
                int processedCandles = timelineIndex + 1;
                if (processedCandles == 1
                    || processedCandles == totalTimelineSteps
                    || processedCandles % progressMilestone == 0) {
                    progressListener.onProgress(
                        new BacktestSimulationProgress(processedCandles, totalTimelineSteps, currentTimestamp)
                    );
                }
            }
        }

        if (activeSymbol != null) {
            Map<String, Integer> finalIndexMap = buildCurrentIndexMap(indexByTimestamp, timeline.get(timeline.size() - 1));
            BacktestStrategyContext finalContext = new BacktestStrategyContext(scopedCandles, finalIndexMap, primarySymbol, null);
            ExitSnapshot exit = exitPosition(
                activeSide,
                quantity,
                entryValue,
                finalContext.currentClose(activeSymbol),
                costRate
            );
            cash = cash.add(exit.cashReleased(), MC);
            tradeReturns.add(exit.tradeReturn());
            tradeSamples.add(createTradeSample(
                activeSymbol,
                activeSide,
                entryTimestamp,
                timeline.get(timeline.size() - 1),
                entryPrice,
                exit.exitPrice(),
                quantity,
                entryValue,
                exit.cashReleased(),
                exit.tradeReturn()
            ));
            if (exit.tradeReturn().compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
            }
            equitySamples.add(new BacktestEquityPointSample(timeline.get(timeline.size() - 1), cash, BigDecimal.ZERO));
        }

        List<BacktestEquityPointSample> equitySeries = applyDrawdownSeries(equitySamples);
        BacktestSimulationResult metrics = metricsCalculator.calculate(
            cash.max(BigDecimal.ONE),
            tradeReturns,
            equitySeries.stream().map(BacktestEquityPointSample::equity).toList(),
            winningTrades
        );
        return new BacktestSimulationResult(
            metrics.finalBalance(),
            metrics.sharpeRatio(),
            metrics.profitFactor(),
            metrics.winRatePercent(),
            metrics.maxDrawdownPercent(),
            metrics.totalTrades(),
            equitySeries,
            tradeSamples
        );
    }

    private Map<String, List<OHLCVData>> scopeCandles(BacktestStrategy strategy, BacktestSimulationRequest request) {
        Map<String, List<OHLCVData>> grouped = request.candles().stream()
            .collect(Collectors.groupingBy(
                OHLCVData::getSymbol,
                Collectors.collectingAndThen(Collectors.toList(), candles -> candles.stream()
                    .sorted(Comparator.comparing(OHLCVData::getTimestamp))
                    .toList())
            ));

        if (strategy.getSelectionMode() == BacktestStrategySelectionMode.SINGLE_SYMBOL) {
            List<OHLCVData> singleSymbolCandles = grouped.get(request.primarySymbol());
            if (singleSymbolCandles == null || singleSymbolCandles.isEmpty()) {
                throw new IllegalArgumentException("Dataset does not contain symbol: " + request.primarySymbol());
            }
            return Map.of(request.primarySymbol(), singleSymbolCandles);
        }

        return grouped;
    }

    private List<LocalDateTime> buildTimeline(BacktestStrategy strategy, Map<String, List<OHLCVData>> scopedCandles) {
        if (strategy.getSelectionMode() == BacktestStrategySelectionMode.SINGLE_SYMBOL) {
            return scopedCandles.values().iterator().next().stream()
                .map(OHLCVData::getTimestamp)
                .toList();
        }

        Set<LocalDateTime> intersection = null;
        for (List<OHLCVData> candles : scopedCandles.values()) {
            Set<LocalDateTime> timestamps = candles.stream()
                .map(OHLCVData::getTimestamp)
                .collect(Collectors.toCollection(HashSet::new));
            if (intersection == null) {
                intersection = timestamps;
            } else {
                intersection.retainAll(timestamps);
            }
        }

        if (intersection == null) {
            return List.of();
        }

        return intersection.stream()
            .sorted()
            .toList();
    }

    private void validateCandles(BacktestStrategy strategy,
                                 Map<String, List<OHLCVData>> scopedCandles,
                                 List<LocalDateTime> timeline) {
        if (scopedCandles.isEmpty()) {
            throw new IllegalArgumentException("Historical data cannot be null or empty");
        }

        if (strategy.getSelectionMode() == BacktestStrategySelectionMode.DATASET_UNIVERSE) {
            long eligibleSymbols = scopedCandles.values().stream()
                .filter(candles -> candles.size() >= strategy.getMinimumCandles())
                .count();
            if (eligibleSymbols < 2) {
                throw new IllegalArgumentException(
                    "Dataset-universe strategies require at least 2 symbols with " + strategy.getMinimumCandles() + " candles."
                );
            }
        }

        if (timeline.size() < strategy.getMinimumCandles()) {
            throw new IllegalArgumentException(
                "Not enough candles for selected strategy. Need at least " + strategy.getMinimumCandles() + " rows."
            );
        }
    }

    private Map<String, Map<LocalDateTime, Integer>> buildIndexByTimestamp(Map<String, List<OHLCVData>> scopedCandles) {
        Map<String, Map<LocalDateTime, Integer>> indexByTimestamp = new HashMap<>();
        for (Map.Entry<String, List<OHLCVData>> entry : scopedCandles.entrySet()) {
            Map<LocalDateTime, Integer> indexMap = new HashMap<>();
            List<OHLCVData> candles = entry.getValue();
            for (int index = 0; index < candles.size(); index++) {
                indexMap.put(candles.get(index).getTimestamp(), index);
            }
            indexByTimestamp.put(entry.getKey(), indexMap);
        }
        return indexByTimestamp;
    }

    private Map<String, Integer> buildCurrentIndexMap(Map<String, Map<LocalDateTime, Integer>> indexByTimestamp,
                                                      LocalDateTime timestamp) {
        return indexByTimestamp.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getOrDefault(timestamp, -1)
            ));
    }

    private String resolvePrimarySymbol(BacktestStrategy strategy,
                                        String requestedSymbol,
                                        Map<String, List<OHLCVData>> scopedCandles) {
        if (strategy.getSelectionMode() == BacktestStrategySelectionMode.SINGLE_SYMBOL) {
            return requestedSymbol;
        }

        if (requestedSymbol != null && scopedCandles.containsKey(requestedSymbol)) {
            return requestedSymbol;
        }

        return scopedCandles.keySet().stream().sorted().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No symbols available for strategy execution"));
    }

    private BacktestOpenPosition buildOpenPosition(String activeSymbol,
                                                   PositionSide activeSide,
                                                   BigDecimal quantity,
                                                   BigDecimal entryPrice,
                                                   BigDecimal entryValue,
                                                   BigDecimal allocationFraction,
                                                   int entryTimelineIndex,
                                                   int timelineIndex) {
        if (activeSymbol == null) {
            return null;
        }
        return new BacktestOpenPosition(
            activeSymbol,
            activeSide,
            quantity,
            entryPrice,
            entryValue,
            allocationFraction,
            entryTimelineIndex,
            timelineIndex - entryTimelineIndex
        );
    }

    private BacktestStrategyDecision queueNextBarDecision(String activeSymbol,
                                                          PositionSide activeSide,
                                                          BacktestStrategyDecision decision) {
        if (decision == null || decision.action() == BacktestStrategyAction.HOLD) {
            return null;
        }

        if (activeSymbol == null) {
            return decision.targetSymbol() != null
                && (decision.action() == BacktestStrategyAction.BUY || decision.action() == BacktestStrategyAction.SHORT)
                ? decision
                : null;
        }

        if (shouldExitPosition(activeSide, decision.action())) {
            return decision;
        }

        if (activeSide == PositionSide.LONG
            && decision.action() == BacktestStrategyAction.ROTATE
            && decision.targetSymbol() != null
            && !decision.targetSymbol().equalsIgnoreCase(activeSymbol)) {
            return decision;
        }

        return null;
    }

    private BigDecimal normalizeAllocation(BigDecimal allocationFraction) {
        if (allocationFraction == null || allocationFraction.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return allocationFraction.min(BigDecimal.ONE);
    }

    private EntrySnapshot enterPosition(String symbol,
                                        PositionSide side,
                                        BigDecimal allocationFraction,
                                        BigDecimal marketPrice,
                                        BigDecimal availableCash,
                                        BigDecimal costRate) {
        BigDecimal executionPrice = side == PositionSide.LONG
            ? marketPrice.multiply(BigDecimal.ONE.add(costRate), MC)
            : marketPrice.multiply(BigDecimal.ONE.subtract(costRate), MC);
        BigDecimal deployableCapital = availableCash.multiply(allocationFraction, MC);
        BigDecimal quantity = deployableCapital.divide(executionPrice, POSITION_SCALE, RoundingMode.HALF_UP);
        BigDecimal entryValue = quantity.multiply(executionPrice, MC);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0 || entryValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return new EntrySnapshot(
            symbol,
            quantity,
            executionPrice,
            entryValue,
            availableCash.subtract(entryValue, MC),
            allocationFraction
        );
    }

    private ExitSnapshot exitPosition(PositionSide side,
                                      BigDecimal quantity,
                                      BigDecimal entryValue,
                                      BigDecimal marketPrice,
                                      BigDecimal costRate) {
        if (side == PositionSide.LONG) {
            BigDecimal effectiveSell = marketPrice.multiply(BigDecimal.ONE.subtract(costRate), MC);
            BigDecimal exitValue = quantity.multiply(effectiveSell, MC);
            BigDecimal tradeReturn = exitValue.subtract(entryValue, MC).divide(entryValue, MC);
            return new ExitSnapshot(exitValue, effectiveSell, tradeReturn);
        }

        BigDecimal effectiveCover = marketPrice.multiply(BigDecimal.ONE.add(costRate), MC);
        BigDecimal coverCost = quantity.multiply(effectiveCover, MC);
        BigDecimal exitValue = entryValue.multiply(BigDecimal.valueOf(2), MC).subtract(coverCost, MC);
        BigDecimal tradeReturn = exitValue.subtract(entryValue, MC).divide(entryValue, MC);
        return new ExitSnapshot(exitValue, effectiveCover, tradeReturn);
    }

    private boolean shouldExitPosition(PositionSide activeSide, BacktestStrategyAction action) {
        if (activeSide == null) {
            return false;
        }
        return (activeSide == PositionSide.LONG && action == BacktestStrategyAction.SELL)
            || (activeSide == PositionSide.SHORT && action == BacktestStrategyAction.COVER);
    }

    private BigDecimal markToMarketEquity(PositionSide side,
                                          BigDecimal cash,
                                          BigDecimal quantity,
                                          BigDecimal entryPrice,
                                          BigDecimal entryValue,
                                          BigDecimal currentPrice) {
        if (side == PositionSide.LONG) {
            return cash.add(quantity.multiply(currentPrice, MC), MC);
        }

        BigDecimal unrealizedPnl = entryPrice.subtract(currentPrice, MC).multiply(quantity, MC);
        return cash.add(entryValue, MC).add(unrealizedPnl, MC);
    }

    private List<BacktestEquityPointSample> applyDrawdownSeries(List<BacktestEquityPointSample> rawSeries) {
        List<BacktestEquityPointSample> finalized = new ArrayList<>();
        BigDecimal peak = rawSeries.get(0).equity();

        for (BacktestEquityPointSample point : rawSeries) {
            if (point.equity().compareTo(peak) > 0) {
                peak = point.equity();
            }
            BigDecimal drawdownPct = peak.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : peak.subtract(point.equity(), MC)
                    .divide(peak, MC)
                    .multiply(BigDecimal.valueOf(100), MC)
                    .setScale(4, RoundingMode.HALF_UP);
            finalized.add(new BacktestEquityPointSample(point.timestamp(), point.equity(), drawdownPct));
        }

        return finalized;
    }

    private BacktestTradeSample createTradeSample(String symbol,
                                                  PositionSide side,
                                                  LocalDateTime entryTimestamp,
                                                  LocalDateTime exitTimestamp,
                                                  BigDecimal entryPrice,
                                                  BigDecimal exitPrice,
                                                  BigDecimal quantity,
                                                  BigDecimal entryValue,
                                                  BigDecimal exitValue,
                                                  BigDecimal tradeReturn) {
        return new BacktestTradeSample(
            symbol,
            side,
            entryTimestamp,
            exitTimestamp,
            entryPrice.setScale(8, RoundingMode.HALF_UP),
            exitPrice.setScale(8, RoundingMode.HALF_UP),
            quantity.setScale(POSITION_SCALE, RoundingMode.HALF_UP),
            entryValue.setScale(8, RoundingMode.HALF_UP),
            exitValue.setScale(8, RoundingMode.HALF_UP),
            tradeReturn.multiply(BigDecimal.valueOf(100), MC).setScale(4, RoundingMode.HALF_UP)
        );
    }

    private record EntrySnapshot(
        String symbol,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal entryValue,
        BigDecimal remainingCash,
        BigDecimal allocationFraction
    ) {
    }

    private record ExitSnapshot(BigDecimal cashReleased, BigDecimal exitPrice, BigDecimal tradeReturn) {
    }
}
