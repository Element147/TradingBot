package com.algotrader.bot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing backtest results for strategy validation.
 * Stores performance metrics and validation status for backtested trading strategies.
 */
@Entity
@Table(name = "backtest_results", indexes = {
    @Index(name = "idx_backtest_strategy", columnList = "strategy_id"),
    @Index(name = "idx_backtest_symbol", columnList = "symbol"),
    @Index(name = "idx_backtest_timestamp", columnList = "timestamp"),
    @Index(name = "idx_backtest_experiment_key_timestamp", columnList = "experiment_key,timestamp"),
    @Index(name = "idx_backtest_dataset_timestamp", columnList = "dataset_id,timestamp"),
    @Index(name = "idx_backtest_execution_status_timestamp", columnList = "execution_status,timestamp")
})
public class BacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 3, max = 50)
    @Column(nullable = false, length = 50)
    private String strategyId;

    @Column
    private Long datasetId;

    @Size(max = 100)
    @Column(length = 100)
    private String datasetName;

    @Size(max = 120)
    @Column(length = 120)
    private String experimentName;

    @Size(max = 120)
    @Column(length = 120)
    private String experimentKey;

    @NotNull
    @Size(min = 3, max = 20)
    @Column(nullable = false, length = 20)
    private String symbol;

    @NotNull
    @Size(min = 1, max = 10)
    @Column(nullable = false, length = 10)
    private String timeframe;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime startDate;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime endDate;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal initialBalance;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal finalBalance;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal profitFactor;

    @NotNull
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal winRate;

    @NotNull
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal maxDrawdown;

    @NotNull
    @Column(nullable = false)
    private Integer totalTrades;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationStatus validationStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus executionStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionStage executionStage;

    @NotNull
    @Column(nullable = false)
    private Integer feesBps;

    @NotNull
    @Column(nullable = false)
    private Integer slippageBps;

    @Column
    private String errorMessage;

    @NotNull
    @Column(nullable = false)
    private Integer progressPercent;

    @NotNull
    @Column(nullable = false)
    private Integer processedCandles;

    @NotNull
    @Column(nullable = false)
    private Integer totalCandles;

    @Column
    private LocalDateTime currentDataTimestamp;

    @Size(max = 500)
    @Column(length = 500)
    private String statusMessage;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime lastProgressAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "backtestResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pointTimestamp ASC")
    private List<BacktestEquityPoint> equityPoints = new ArrayList<>();

    @OneToMany(mappedBy = "backtestResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("entryTime ASC")
    private List<BacktestTradeSeriesItem> tradeSeries = new ArrayList<>();

    /**
     * Validation status enum for backtest results
     */
    public enum ValidationStatus {
        PENDING,
        PASSED,
        FAILED,
        PRODUCTION_READY
    }

    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public enum ExecutionStage {
        QUEUED,
        VALIDATING_REQUEST,
        LOADING_DATASET,
        FILTERING_CANDLES,
        SIMULATING,
        PERSISTING_RESULTS,
        COMPLETED,
        FAILED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (executionStatus == null) {
            executionStatus = ExecutionStatus.PENDING;
        }
        if (executionStage == null) {
            executionStage = ExecutionStage.QUEUED;
        }
        if (feesBps == null) {
            feesBps = 10;
        }
        if (slippageBps == null) {
            slippageBps = 3;
        }
        if (progressPercent == null) {
            progressPercent = 0;
        }
        if (processedCandles == null) {
            processedCandles = 0;
        }
        if (totalCandles == null) {
            totalCandles = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public BacktestResult() {
    }

    public BacktestResult(String strategyId, String symbol, LocalDateTime startDate,
                          LocalDateTime endDate, BigDecimal initialBalance, BigDecimal finalBalance,
                          BigDecimal sharpeRatio, BigDecimal profitFactor, BigDecimal winRate,
                          BigDecimal maxDrawdown, Integer totalTrades, ValidationStatus validationStatus) {
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.timeframe = "1h";
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialBalance = initialBalance;
        this.finalBalance = finalBalance;
        this.sharpeRatio = sharpeRatio;
        this.profitFactor = profitFactor;
        this.winRate = winRate;
        this.maxDrawdown = maxDrawdown;
        this.totalTrades = totalTrades;
        this.validationStatus = validationStatus;
        this.executionStatus = ExecutionStatus.COMPLETED;
        this.executionStage = ExecutionStage.COMPLETED;
        this.feesBps = 10;
        this.slippageBps = 3;
        this.timestamp = LocalDateTime.now();
        this.progressPercent = 100;
        this.processedCandles = 0;
        this.totalCandles = 0;
        this.currentDataTimestamp = endDate;
        this.statusMessage = "Completed.";
        this.lastProgressAt = this.timestamp;
        this.startedAt = this.timestamp;
        this.completedAt = this.timestamp;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getExperimentKey() {
        return experimentKey;
    }

    public void setExperimentKey(String experimentKey) {
        this.experimentKey = experimentKey;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public BigDecimal getFinalBalance() {
        return finalBalance;
    }

    public void setFinalBalance(BigDecimal finalBalance) {
        this.finalBalance = finalBalance;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public BigDecimal getProfitFactor() {
        return profitFactor;
    }

    public void setProfitFactor(BigDecimal profitFactor) {
        this.profitFactor = profitFactor;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public void setWinRate(BigDecimal winRate) {
        this.winRate = winRate;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public void setMaxDrawdown(BigDecimal maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public Integer getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(Integer totalTrades) {
        this.totalTrades = totalTrades;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public ExecutionStage getExecutionStage() {
        return executionStage;
    }

    public void setExecutionStage(ExecutionStage executionStage) {
        this.executionStage = executionStage;
    }

    public Integer getFeesBps() {
        return feesBps;
    }

    public void setFeesBps(Integer feesBps) {
        this.feesBps = feesBps;
    }

    public Integer getSlippageBps() {
        return slippageBps;
    }

    public void setSlippageBps(Integer slippageBps) {
        this.slippageBps = slippageBps;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getProcessedCandles() {
        return processedCandles;
    }

    public void setProcessedCandles(Integer processedCandles) {
        this.processedCandles = processedCandles;
    }

    public Integer getTotalCandles() {
        return totalCandles;
    }

    public void setTotalCandles(Integer totalCandles) {
        this.totalCandles = totalCandles;
    }

    public LocalDateTime getCurrentDataTimestamp() {
        return currentDataTimestamp;
    }

    public void setCurrentDataTimestamp(LocalDateTime currentDataTimestamp) {
        this.currentDataTimestamp = currentDataTimestamp;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getLastProgressAt() {
        return lastProgressAt;
    }

    public void setLastProgressAt(LocalDateTime lastProgressAt) {
        this.lastProgressAt = lastProgressAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<BacktestEquityPoint> getEquityPoints() {
        return equityPoints;
    }

    public void replaceEquityPoints(List<BacktestEquityPoint> nextEquityPoints) {
        equityPoints.clear();
        if (nextEquityPoints == null) {
            return;
        }
        nextEquityPoints.forEach(this::addEquityPoint);
    }

    public void addEquityPoint(BacktestEquityPoint equityPoint) {
        equityPoint.setBacktestResult(this);
        equityPoints.add(equityPoint);
    }

    public List<BacktestTradeSeriesItem> getTradeSeries() {
        return tradeSeries;
    }

    public void replaceTradeSeries(List<BacktestTradeSeriesItem> nextTradeSeries) {
        tradeSeries.clear();
        if (nextTradeSeries == null) {
            return;
        }
        nextTradeSeries.forEach(this::addTradeSeriesItem);
    }

    public void addTradeSeriesItem(BacktestTradeSeriesItem tradeSeriesItem) {
        tradeSeriesItem.setBacktestResult(this);
        tradeSeries.add(tradeSeriesItem);
    }

    @Override
    public String toString() {
        return "BacktestResult{" +
                "id=" + id +
                ", strategyId='" + strategyId + '\'' +
                ", datasetId=" + datasetId +
                ", datasetName='" + datasetName + '\'' +
                ", experimentName='" + experimentName + '\'' +
                ", symbol='" + symbol + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", initialBalance=" + initialBalance +
                ", finalBalance=" + finalBalance +
                ", sharpeRatio=" + sharpeRatio +
                ", profitFactor=" + profitFactor +
                ", winRate=" + winRate +
                ", maxDrawdown=" + maxDrawdown +
                ", totalTrades=" + totalTrades +
                ", validationStatus=" + validationStatus +
                ", executionStatus=" + executionStatus +
                ", executionStage=" + executionStage +
                ", progressPercent=" + progressPercent +
                ", timestamp=" + timestamp +
                '}';
    }
}
