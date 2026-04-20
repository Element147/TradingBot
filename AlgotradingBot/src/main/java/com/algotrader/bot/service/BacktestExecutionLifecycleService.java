package com.algotrader.bot.service;

import com.algotrader.bot.backtest.BacktestSimulationResult;
import com.algotrader.bot.entity.BacktestEquityPoint;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.repository.BacktestResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class BacktestExecutionLifecycleService {

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestProgressService backtestProgressService;
    private final TransactionTemplate transactionTemplate;

    public BacktestExecutionLifecycleService(BacktestResultRepository backtestResultRepository,
                                             BacktestProgressService backtestProgressService,
                                             PlatformTransactionManager transactionManager) {
        this.backtestResultRepository = backtestResultRepository;
        this.backtestProgressService = backtestProgressService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BacktestExecutionContext markRunStarted(Long backtestId) {
        return transactionTemplate.execute(status -> {
            BacktestResult result = backtestResultRepository.findById(backtestId)
                .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

            LocalDateTime now = LocalDateTime.now();
            result.setExecutionStatus(BacktestResult.ExecutionStatus.RUNNING);
            result.setExecutionStage(BacktestResult.ExecutionStage.VALIDATING_REQUEST);
            result.setStartedAt(now);
            result.setLastProgressAt(now);
            result.setProgressPercent(2);
            result.setProcessedCandles(0);
            result.setTotalCandles(0);
            result.setCurrentDataTimestamp(null);
            result.setStatusMessage("Validation passed. Preparing execution.");
            result.setErrorMessage(null);
            backtestResultRepository.save(result);
            backtestProgressService.publish(result);

            return new BacktestExecutionContext(
                result.getId(),
                result.getStrategyId(),
                result.getDatasetId(),
                result.getSymbol(),
                result.getTimeframe(),
                result.getStartDate(),
                result.getEndDate(),
                result.getInitialBalance(),
                result.getFeesBps(),
                result.getSlippageBps()
            );
        });
    }

    public void updateProgress(Long backtestId,
                               BacktestResult.ExecutionStage executionStage,
                               int progressPercent,
                               int processedCandles,
                               int totalCandles,
                               LocalDateTime currentDataTimestamp,
                               String statusMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            BacktestResult result = backtestResultRepository.findById(backtestId)
                .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

            result.setExecutionStage(executionStage);
            result.setProgressPercent(Math.max(0, Math.min(progressPercent, 100)));
            result.setProcessedCandles(Math.max(processedCandles, 0));
            result.setTotalCandles(Math.max(totalCandles, 0));
            result.setCurrentDataTimestamp(currentDataTimestamp);
            result.setStatusMessage(statusMessage);
            result.setLastProgressAt(LocalDateTime.now());
            backtestResultRepository.save(result);
            backtestProgressService.publish(result);
        });
    }

    public void persistCompletedResult(Long backtestId,
                                       BacktestSimulationResult simulationResult,
                                       int totalCandles,
                                       LocalDateTime currentDataTimestamp) {
        transactionTemplate.executeWithoutResult(status -> {
            BacktestResult result = backtestResultRepository.findById(backtestId)
                .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

            applySimulationResult(result, simulationResult);
            LocalDateTime now = LocalDateTime.now();
            result.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
            result.setExecutionStage(BacktestResult.ExecutionStage.COMPLETED);
            result.setProgressPercent(100);
            result.setProcessedCandles(totalCandles);
            result.setTotalCandles(totalCandles);
            result.setCurrentDataTimestamp(currentDataTimestamp);
            result.setStatusMessage("Backtest completed. Metrics and trade series are ready to review.");
            result.setLastProgressAt(now);
            result.setCompletedAt(now);
            result.setErrorMessage(null);
            backtestResultRepository.save(result);
            backtestProgressService.publish(result);
        });
    }

    public void markRunFailed(Long backtestId, Exception exception) {
        transactionTemplate.executeWithoutResult(status -> {
            BacktestResult result = backtestResultRepository.findById(backtestId)
                .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

            LocalDateTime now = LocalDateTime.now();
            result.setExecutionStatus(BacktestResult.ExecutionStatus.FAILED);
            result.setExecutionStage(BacktestResult.ExecutionStage.FAILED);
            result.setValidationStatus(BacktestResult.ValidationStatus.FAILED);
            result.setStatusMessage("Execution failed: " + exception.getMessage());
            result.setErrorMessage(exception.getMessage());
            result.setLastProgressAt(now);
            result.setCompletedAt(now);
            backtestResultRepository.save(result);
            backtestProgressService.publish(result);
        });
    }

    private void applySimulationResult(BacktestResult result, BacktestSimulationResult simulationResult) {
        result.setFinalBalance(simulationResult.finalBalance());
        result.setSharpeRatio(simulationResult.sharpeRatio());
        result.setProfitFactor(simulationResult.profitFactor());
        result.setWinRate(simulationResult.winRatePercent());
        result.setMaxDrawdown(simulationResult.maxDrawdownPercent());
        result.setTotalTrades(simulationResult.totalTrades());
        result.setValidationStatus(isPassed(simulationResult)
            ? BacktestResult.ValidationStatus.PASSED
            : BacktestResult.ValidationStatus.FAILED);
        result.replaceEquityPoints(simulationResult.equitySeries().stream().map(sample -> {
            BacktestEquityPoint point = new BacktestEquityPoint();
            point.setPointTimestamp(sample.timestamp());
            point.setEquity(sample.equity());
            point.setDrawdownPct(sample.drawdownPct());
            return point;
        }).toList());
        result.replaceTradeSeries(simulationResult.tradeSeries().stream().map(sample -> {
            BacktestTradeSeriesItem item = new BacktestTradeSeriesItem();
            item.setSymbol(sample.symbol());
            item.setPositionSide(sample.side());
            item.setEntryTime(sample.entryTime());
            item.setExitTime(sample.exitTime());
            item.setEntryPrice(sample.entryPrice());
            item.setExitPrice(sample.exitPrice());
            item.setQuantity(sample.quantity());
            item.setEntryValue(sample.entryValue());
            item.setExitValue(sample.exitValue());
            item.setReturnPct(sample.returnPct());
            return item;
        }).toList());
    }

    private boolean isPassed(BacktestSimulationResult simulationResult) {
        return simulationResult.sharpeRatio().compareTo(BigDecimal.ONE) >= 0
            && simulationResult.profitFactor().compareTo(new BigDecimal("1.5")) >= 0
            && simulationResult.maxDrawdownPercent().compareTo(new BigDecimal("25")) < 0;
    }

    public record BacktestExecutionContext(
        Long backtestId,
        String strategyId,
        Long datasetId,
        String symbol,
        String timeframe,
        LocalDateTime startDate,
        LocalDateTime endDate,
        BigDecimal initialBalance,
        Integer feesBps,
        Integer slippageBps
    ) {
    }
}
