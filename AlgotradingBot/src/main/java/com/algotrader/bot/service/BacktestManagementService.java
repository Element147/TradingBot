package com.algotrader.bot.service;

import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.controller.BacktestAlgorithmResponse;
import com.algotrader.bot.controller.BacktestRunResponse;
import com.algotrader.bot.controller.RunBacktestRequest;
import com.algotrader.bot.controller.AsyncTaskMonitorResponse;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.repository.BacktestResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class BacktestManagementService {

    private static final Logger logger = LoggerFactory.getLogger(BacktestManagementService.class);
    private static final String DATASET_UNIVERSE_LABEL = "DATASET_UNIVERSE";

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestExecutionService backtestExecutionService;
    private final BacktestDatasetStorageService backtestDatasetStorageService;
    private final BacktestDatasetLifecycleService backtestDatasetLifecycleService;
    private final BacktestStrategyRegistry backtestStrategyRegistry;
    private final BacktestProgressService backtestProgressService;
    private final OperatorAuditService operatorAuditService;

    public BacktestManagementService(BacktestResultRepository backtestResultRepository,
                                     BacktestExecutionService backtestExecutionService,
                                     BacktestDatasetStorageService backtestDatasetStorageService,
                                     BacktestDatasetLifecycleService backtestDatasetLifecycleService,
                                     BacktestStrategyRegistry backtestStrategyRegistry,
                                     BacktestProgressService backtestProgressService,
                                     OperatorAuditService operatorAuditService) {
        this.backtestResultRepository = backtestResultRepository;
        this.backtestExecutionService = backtestExecutionService;
        this.backtestDatasetStorageService = backtestDatasetStorageService;
        this.backtestDatasetLifecycleService = backtestDatasetLifecycleService;
        this.backtestStrategyRegistry = backtestStrategyRegistry;
        this.backtestProgressService = backtestProgressService;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional(readOnly = true)
    public List<BacktestAlgorithmResponse> getAlgorithms() {
        return backtestStrategyRegistry.getDefinitions().stream()
            .map(definition -> new BacktestAlgorithmResponse(
                definition.type().name(),
                definition.label(),
                definition.description(),
                definition.selectionMode().name()))
            .toList();
    }

    @Transactional
    public BacktestRunResponse runBacktest(RunBacktestRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        if (request.initialBalance().compareTo(new BigDecimal("100.00")) <= 0) {
            throw new IllegalArgumentException("Initial balance must be greater than 100");
        }

        backtestDatasetLifecycleService.validateDatasetAvailableForNewRuns(request.datasetId());
        BacktestAlgorithmType algorithmType = BacktestAlgorithmType.from(request.algorithmType());
        var strategyDefinition = backtestStrategyRegistry.getStrategy(algorithmType).definition();

        BacktestDataset dataset = backtestDatasetStorageService.getDataset(request.datasetId());
        String effectiveSymbol = resolveRequestedSymbol(
            request.symbol(),
            dataset.getSymbolsCsv(),
            strategyDefinition.selectionMode()
        );
        String experimentName = resolveRequestedExperimentName(
            request.experimentName(),
            algorithmType.name(),
            dataset.getName(),
            effectiveSymbol,
            request.timeframe(),
            strategyDefinition.selectionMode()
        );

        BacktestResult pending = new BacktestResult(
            algorithmType.name(),
            effectiveSymbol,
            request.startDate().atStartOfDay(),
            request.endDate().atStartOfDay(),
            request.initialBalance(),
            request.initialBalance(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            BacktestResult.ValidationStatus.PENDING
        );

        pending.setDatasetId(dataset.getId());
        pending.setDatasetName(dataset.getName());
        pending.setExperimentName(experimentName);
        pending.setExperimentKey(normalizeExperimentKey(experimentName));
        pending.setTimeframe(request.timeframe());
        pending.setExecutionStatus(BacktestResult.ExecutionStatus.PENDING);
        pending.setExecutionStage(BacktestResult.ExecutionStage.QUEUED);
        pending.setProgressPercent(0);
        pending.setProcessedCandles(0);
        pending.setTotalCandles(0);
        pending.setCurrentDataTimestamp(null);
        pending.setStatusMessage("Queued. Waiting for the backtest worker to start.");
        pending.setLastProgressAt(LocalDateTime.now());
        pending.setStartedAt(null);
        pending.setCompletedAt(null);
        pending.setFeesBps(request.feesBps());
        pending.setSlippageBps(request.slippageBps());
        pending.setTimestamp(LocalDateTime.now());

        BacktestResult saved = backtestResultRepository.save(pending);
        backtestProgressService.publish(saved);
        logger.info(
            "Queued backtest {}: strategy={}, datasetId={}, symbol={}, timeframe={}, experiment={}",
            saved.getId(),
            saved.getStrategyId(),
            saved.getDatasetId(),
            saved.getSymbol(),
            saved.getTimeframe(),
            saved.getExperimentName()
        );

        dispatchAfterCommit(saved.getId());
        operatorAuditService.recordSuccess(
            "BACKTEST_RUN_STARTED",
            "test",
            "BACKTEST",
            String.valueOf(saved.getId()),
            "strategy=" + saved.getStrategyId() + ", datasetId=" + saved.getDatasetId()
        );

        return new BacktestRunResponse(
            saved.getId(),
            saved.getExecutionStatus().name(),
            saved.getTimestamp(),
            new AsyncTaskMonitorResponse("QUEUED", 0, 1, null, false, false, 120L)
        );
    }

    @Transactional
    public BacktestRunResponse replayBacktest(Long backtestId) {
        BacktestResult existing = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

        if (existing.getDatasetId() == null) {
            throw new IllegalArgumentException("Replay requires a dataset-backed backtest");
        }

        BacktestDataset dataset = backtestDatasetStorageService.getDataset(existing.getDatasetId());

        BacktestResult pending = new BacktestResult(
            existing.getStrategyId(),
            existing.getSymbol(),
            existing.getStartDate(),
            existing.getEndDate(),
            existing.getInitialBalance(),
            existing.getInitialBalance(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            BacktestResult.ValidationStatus.PENDING
        );

        pending.setDatasetId(dataset.getId());
        pending.setDatasetName(dataset.getName());
        pending.setExperimentName(resolveExperimentName(existing));
        pending.setExperimentKey(resolveExperimentKey(existing));
        pending.setTimeframe(existing.getTimeframe());
        pending.setExecutionStatus(BacktestResult.ExecutionStatus.PENDING);
        pending.setExecutionStage(BacktestResult.ExecutionStage.QUEUED);
        pending.setProgressPercent(0);
        pending.setProcessedCandles(0);
        pending.setTotalCandles(0);
        pending.setCurrentDataTimestamp(null);
        pending.setStatusMessage("Replay queued. Waiting for the backtest worker to start.");
        pending.setLastProgressAt(LocalDateTime.now());
        pending.setStartedAt(null);
        pending.setCompletedAt(null);
        pending.setFeesBps(existing.getFeesBps());
        pending.setSlippageBps(existing.getSlippageBps());
        pending.setTimestamp(LocalDateTime.now());

        BacktestResult saved = backtestResultRepository.save(pending);
        backtestProgressService.publish(saved);
        logger.info(
            "Queued replay backtest {} from source {} using dataset {}",
            saved.getId(),
            backtestId,
            dataset.getId()
        );
        dispatchAfterCommit(saved.getId());
        operatorAuditService.recordSuccess(
            "BACKTEST_REPLAY_STARTED",
            "test",
            "BACKTEST",
            String.valueOf(saved.getId()),
            "sourceBacktestId=" + backtestId + ", datasetId=" + dataset.getId()
        );

        return new BacktestRunResponse(
            saved.getId(),
            saved.getExecutionStatus().name(),
            saved.getTimestamp(),
            new AsyncTaskMonitorResponse("QUEUED", 0, 1, null, false, false, 120L)
        );
    }

    @Transactional
    public void deleteBacktest(Long backtestId) {
        BacktestResult result = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

        if (result.getExecutionStatus() == BacktestResult.ExecutionStatus.PENDING
            || result.getExecutionStatus() == BacktestResult.ExecutionStatus.RUNNING) {
            throw new IllegalStateException("Active backtests cannot be deleted while execution is still in progress");
        }

        backtestResultRepository.delete(result);
        logger.info("Deleted backtest {} ({})", backtestId, result.getExperimentName());
        operatorAuditService.recordSuccess(
            "BACKTEST_DELETED",
            "test",
            "BACKTEST",
            String.valueOf(backtestId),
            "strategy=" + result.getStrategyId() + ", datasetId=" + result.getDatasetId()
        );
    }

    private String resolveRequestedSymbol(String requestedSymbol,
                                         String datasetSymbolsCsv,
                                         com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode selectionMode) {
        List<String> supportedSymbols = parseSymbols(datasetSymbolsCsv);
        String normalizedRequestedSymbol = requestedSymbol == null ? null : requestedSymbol.trim();

        if (selectionMode == com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode.DATASET_UNIVERSE) {
            if (normalizedRequestedSymbol != null
                && !normalizedRequestedSymbol.isBlank()
                && !supportedSymbols.contains(normalizedRequestedSymbol)) {
                throw new IllegalArgumentException("Selected primary symbol is not present in dataset: " + normalizedRequestedSymbol);
            }
            return DATASET_UNIVERSE_LABEL;
        }

        if (normalizedRequestedSymbol == null || normalizedRequestedSymbol.isBlank()) {
            throw new IllegalArgumentException("Selected symbol is required for single-symbol strategies");
        }

        if (!supportedSymbols.contains(normalizedRequestedSymbol)) {
            throw new IllegalArgumentException("Selected symbol is not present in dataset: " + normalizedRequestedSymbol);
        }

        return normalizedRequestedSymbol;
    }

    private List<String> parseSymbols(String datasetSymbolsCsv) {
        return List.of(datasetSymbolsCsv.split(",")).stream()
            .map(String::trim)
            .filter(symbol -> !symbol.isBlank())
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));
    }

    private String resolveRequestedExperimentName(String requestedExperimentName,
                                                  String strategyId,
                                                  String datasetName,
                                                  String effectiveSymbol,
                                                  String timeframe,
                                                  com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode selectionMode) {
        if (requestedExperimentName != null && !requestedExperimentName.isBlank()) {
            return requestedExperimentName.trim();
        }

        String marketLabel = selectionMode == com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode.DATASET_UNIVERSE
            ? DATASET_UNIVERSE_LABEL
            : effectiveSymbol;
        return strategyId + " | " + datasetName + " | " + marketLabel + " | " + timeframe;
    }

    private String resolveExperimentName(BacktestResult result) {
        if (result.getExperimentName() != null && !result.getExperimentName().isBlank()) {
            return result.getExperimentName();
        }

        String datasetLabel = result.getDatasetName() == null || result.getDatasetName().isBlank()
            ? "dataset-" + (result.getDatasetId() == null ? "unknown" : result.getDatasetId())
            : result.getDatasetName();
        return result.getStrategyId() + " | " + datasetLabel + " | " + result.getSymbol() + " | " + result.getTimeframe();
    }

    private String resolveExperimentKey(BacktestResult result) {
        if (result.getExperimentKey() != null && !result.getExperimentKey().isBlank()) {
            return result.getExperimentKey();
        }
        return normalizeExperimentKey(resolveExperimentName(result));
    }

    private String normalizeExperimentKey(String experimentName) {
        return experimentName
            .trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    private void dispatchAfterCommit(Long backtestId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    backtestExecutionService.executeAsync(backtestId);
                }
            });
            return;
        }

        backtestExecutionService.executeAsync(backtestId);
    }
}
