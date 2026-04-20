package com.algotrader.bot.service;

import com.algotrader.bot.backtest.BacktestSimulationEngine;
import com.algotrader.bot.backtest.BacktestSimulationProgress;
import com.algotrader.bot.backtest.BacktestSimulationRequest;
import com.algotrader.bot.backtest.BacktestSimulationResult;
import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.service.marketdata.MarketDataQueryMode;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BacktestExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(BacktestExecutionService.class);
    private static final int PREPARATION_PROGRESS_PERCENT = 20;
    private static final int PERSISTING_PROGRESS_PERCENT = 98;

    private final BacktestDatasetStorageService backtestDatasetStorageService;
    private final BacktestSimulationEngine backtestSimulationEngine;
    private final BacktestStrategyRegistry backtestStrategyRegistry;
    private final MarketDataQueryService marketDataQueryService;
    private final BacktestExecutionLifecycleService backtestExecutionLifecycleService;
    private final BackendOperationMetrics backendOperationMetrics;
    private final ConcurrentMap<Long, Boolean> inFlightBacktests = new ConcurrentHashMap<>();

    public BacktestExecutionService(BacktestDatasetStorageService backtestDatasetStorageService,
                                    BacktestSimulationEngine backtestSimulationEngine,
                                    BacktestStrategyRegistry backtestStrategyRegistry,
                                    MarketDataQueryService marketDataQueryService,
                                    BacktestExecutionLifecycleService backtestExecutionLifecycleService,
                                    BackendOperationMetrics backendOperationMetrics) {
        this.backtestDatasetStorageService = backtestDatasetStorageService;
        this.backtestSimulationEngine = backtestSimulationEngine;
        this.backtestStrategyRegistry = backtestStrategyRegistry;
        this.marketDataQueryService = marketDataQueryService;
        this.backtestExecutionLifecycleService = backtestExecutionLifecycleService;
        this.backendOperationMetrics = backendOperationMetrics;
    }

    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<Void> executeAsync(Long backtestId) {
        if (inFlightBacktests.putIfAbsent(backtestId, Boolean.TRUE) != null) {
            logger.info("Backtest {} is already scheduled or running in this JVM. Skipping duplicate dispatch.", backtestId);
            return CompletableFuture.completedFuture(null);
        }

        try {
            long executionStartedAt = System.nanoTime();
            BacktestExecutionLifecycleService.BacktestExecutionContext context =
                backtestExecutionLifecycleService.markRunStarted(backtestId);
            logger.info(
                "Backtest {} started: strategy={}, datasetId={}, symbol={}, timeframe={}, range={} to {}",
                backtestId,
                context.strategyId(),
                context.datasetId(),
                context.symbol(),
                context.timeframe(),
                context.startDate(),
                context.endDate()
            );

            backtestExecutionLifecycleService.updateProgress(
                backtestId,
                BacktestResult.ExecutionStage.LOADING_DATASET,
                5,
                0,
                0,
                null,
                "Loading the requested candle window from the market-data store."
            );

            long datasetLookupStartedAt = System.nanoTime();
            BacktestDataset dataset = backtestDatasetStorageService.getDataset(context.datasetId());
            backendOperationMetrics.record(
                "async",
                "backtest_execution_startup",
                "dataset_lookup",
                System.nanoTime() - datasetLookupStartedAt,
                1,
                0L
            );
            logger.info(
                "Backtest {} loading dataset {} ({}, {} rows, symbols={})",
                backtestId,
                dataset.getId(),
                dataset.getName(),
                dataset.getRowCount(),
                dataset.getSymbolsCsv()
            );

            BacktestAlgorithmType algorithmType = BacktestAlgorithmType.from(context.strategyId());
            String primarySymbol = resolvePrimarySymbol(context.symbol(), dataset.getSymbolsCsv());
            Set<String> requestedSymbols = resolveRequestedSymbols(algorithmType, primarySymbol);
            long candleQueryStartedAt = System.nanoTime();
            List<OHLCVData> simulationCandles = marketDataQueryService.queryCandlesForDataset(
                    context.datasetId(),
                    context.timeframe(),
                    context.startDate(),
                    context.endDate(),
                    requestedSymbols,
                    MarketDataQueryMode.BEST_AVAILABLE
                ).candles().stream()
                .map(candle -> candle.toOhlcvData())
                .sorted(Comparator.comparing(OHLCVData::getTimestamp).thenComparing(OHLCVData::getSymbol))
                .toList();
            backendOperationMetrics.record(
                "async",
                "backtest_execution_startup",
                "candle_query",
                System.nanoTime() - candleQueryStartedAt,
                simulationCandles.size(),
                0L
            );

            backtestExecutionLifecycleService.updateProgress(
                backtestId,
                BacktestResult.ExecutionStage.FILTERING_CANDLES,
                12,
                0,
                simulationCandles.size(),
                null,
                "Loaded the requested candle window from the market-data store."
            );

            LocalDateTime firstFilteredTimestamp = simulationCandles.isEmpty() ? null : simulationCandles.get(0).getTimestamp();
            LocalDateTime lastFilteredTimestamp = simulationCandles.isEmpty() ? null : simulationCandles.get(simulationCandles.size() - 1).getTimestamp();
            logger.info(
                "Backtest {} prepared {} simulation candles at timeframe {} between {} and {}",
                backtestId,
                simulationCandles.size(),
                context.timeframe(),
                firstFilteredTimestamp,
                lastFilteredTimestamp
            );
            backendOperationMetrics.record(
                "async",
                "backtest_execution_startup",
                "prepare_request",
                System.nanoTime() - executionStartedAt,
                simulationCandles.size(),
                0L
            );

            backtestExecutionLifecycleService.updateProgress(
                backtestId,
                BacktestResult.ExecutionStage.SIMULATING,
                PREPARATION_PROGRESS_PERCENT,
                0,
                simulationCandles.size(),
                firstFilteredTimestamp,
                simulationCandles.isEmpty()
                    ? "No candles remain after filtering."
                    : "Simulation started. Replaying historical candles at " + context.timeframe() + "."
            );

            AtomicInteger lastLoggedMilestone = new AtomicInteger(-1);
            long simulationStartedAt = System.nanoTime();
            BacktestSimulationResult simulationResult = backtestSimulationEngine.simulate(
                algorithmType,
                new BacktestSimulationRequest(
                    simulationCandles,
                    primarySymbol,
                    context.timeframe(),
                    context.initialBalance(),
                    context.feesBps(),
                    context.slippageBps()
                ),
                progress -> onSimulationProgress(backtestId, progress, lastLoggedMilestone)
            );
            backendOperationMetrics.record(
                "async",
                "backtest_execution",
                "simulation",
                System.nanoTime() - simulationStartedAt,
                simulationCandles.size(),
                0L
            );

            backtestExecutionLifecycleService.updateProgress(
                backtestId,
                BacktestResult.ExecutionStage.PERSISTING_RESULTS,
                PERSISTING_PROGRESS_PERCENT,
                simulationCandles.size(),
                simulationCandles.size(),
                lastFilteredTimestamp,
                "Simulation loop finished. Persisting metrics, equity curve, and trade series."
            );

            backtestExecutionLifecycleService.persistCompletedResult(
                backtestId,
                simulationResult,
                simulationCandles.size(),
                lastFilteredTimestamp
            );
            backendOperationMetrics.record(
                "async",
                "backtest_execution",
                "total",
                System.nanoTime() - executionStartedAt,
                simulationCandles.size(),
                0L
            );
            logger.info(
                "Backtest {} completed: finalBalance={}, trades={}, sharpe={}, profitFactor={}, maxDrawdown={}",
                backtestId,
                simulationResult.finalBalance(),
                simulationResult.totalTrades(),
                simulationResult.sharpeRatio(),
                simulationResult.profitFactor(),
                simulationResult.maxDrawdownPercent()
            );
        } catch (Exception exception) {
            logger.error("Backtest execution failed for id {}", backtestId, exception);
            try {
                backtestExecutionLifecycleService.markRunFailed(backtestId, exception);
            } catch (Exception markFailedException) {
                logger.error("Unable to persist failure state for backtest {}", backtestId, markFailedException);
            }
        } finally {
            inFlightBacktests.remove(backtestId);
        }

        return CompletableFuture.completedFuture(null);
    }

    public int getInFlightBacktestCount() {
        return inFlightBacktests.size();
    }

    private void onSimulationProgress(Long backtestId,
                                      BacktestSimulationProgress progress,
                                      AtomicInteger lastLoggedMilestone) {
        int overallProgress = mapSimulationProgressToOverall(progress.processedCandles(), progress.totalCandles());
        String statusMessage = progress.processedCandles() >= progress.totalCandles()
            ? "Simulation loop finished. Computing final metrics."
            : "Replaying candle " + progress.processedCandles() + " of " + progress.totalCandles() + ".";

        backtestExecutionLifecycleService.updateProgress(
            backtestId,
            BacktestResult.ExecutionStage.SIMULATING,
            overallProgress,
            progress.processedCandles(),
            progress.totalCandles(),
            progress.currentTimestamp(),
            statusMessage
        );

        int milestone = progress.totalCandles() <= 0
            ? 100
            : (int) Math.floor((progress.processedCandles() * 100.0) / progress.totalCandles() / 10.0) * 10;
        if (progress.processedCandles() == progress.totalCandles()
            || (milestone >= 0 && lastLoggedMilestone.getAndSet(milestone) != milestone && milestone % 10 == 0)) {
            logger.info(
                "Backtest {} progress: {}% ({} / {}) current candle={}",
                backtestId,
                overallProgress,
                progress.processedCandles(),
                progress.totalCandles(),
                progress.currentTimestamp()
            );
        }
    }

    private int mapSimulationProgressToOverall(int processedCandles, int totalCandles) {
        if (totalCandles <= 0) {
            return PREPARATION_PROGRESS_PERCENT;
        }
        double ratio = Math.min(1.0, Math.max(0.0, processedCandles / (double) totalCandles));
        return PREPARATION_PROGRESS_PERCENT
            + (int) Math.round(ratio * (PERSISTING_PROGRESS_PERCENT - PREPARATION_PROGRESS_PERCENT - 3));
    }

    private Set<String> resolveRequestedSymbols(BacktestAlgorithmType algorithmType, String primarySymbol) {
        if (backtestStrategyRegistry.getStrategy(algorithmType).getSelectionMode() == BacktestStrategySelectionMode.SINGLE_SYMBOL) {
            return Set.of(primarySymbol);
        }
        return Set.of();
    }

    private String resolvePrimarySymbol(String requestedSymbol, String datasetSymbolsCsv) {
        List<String> datasetSymbols = parseSymbols(datasetSymbolsCsv);
        if (datasetSymbols.contains(requestedSymbol)) {
            return requestedSymbol;
        }
        return datasetSymbols.stream()
            .findFirst()
            .orElse(requestedSymbol);
    }

    private List<String> parseSymbols(String symbolsCsv) {
        return List.of(symbolsCsv.split(",")).stream()
            .map(String::trim)
            .filter(symbol -> !symbol.isBlank())
            .toList();
    }
}
