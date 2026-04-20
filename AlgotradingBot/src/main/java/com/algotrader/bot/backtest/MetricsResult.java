package com.algotrader.bot.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO containing calculated backtest performance metrics.
 * All monetary values use BigDecimal for precision.
 */
public class MetricsResult {

    private final BigDecimal sharpeRatio;
    private final BigDecimal profitFactor;
    private final BigDecimal winRate;
    private final BigDecimal maxDrawdown;
    private final BigDecimal calmarRatio;
    private final BigDecimal totalReturn;
    private final BigDecimal annualReturn;
    private final int totalTrades;
    private final int winningTrades;
    private final int losingTrades;
    private final BigDecimal avgWin;
    private final BigDecimal avgLoss;
    private final BigDecimal pValue;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    public MetricsResult(BigDecimal sharpeRatio, BigDecimal profitFactor, BigDecimal winRate,
                         BigDecimal maxDrawdown, BigDecimal calmarRatio, BigDecimal totalReturn,
                         BigDecimal annualReturn, int totalTrades, int winningTrades, int losingTrades,
                         BigDecimal avgWin, BigDecimal avgLoss, BigDecimal pValue,
                         LocalDateTime startDate, LocalDateTime endDate) {
        this.sharpeRatio = sharpeRatio;
        this.profitFactor = profitFactor;
        this.winRate = winRate;
        this.maxDrawdown = maxDrawdown;
        this.calmarRatio = calmarRatio;
        this.totalReturn = totalReturn;
        this.annualReturn = annualReturn;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.avgWin = avgWin;
        this.avgLoss = avgLoss;
        this.pValue = pValue;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public BigDecimal getProfitFactor() {
        return profitFactor;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public BigDecimal getCalmarRatio() {
        return calmarRatio;
    }

    public BigDecimal getTotalReturn() {
        return totalReturn;
    }

    public BigDecimal getAnnualReturn() {
        return annualReturn;
    }

    public int getTotalTrades() {
        return totalTrades;
    }

    public int getWinningTrades() {
        return winningTrades;
    }

    public int getLosingTrades() {
        return losingTrades;
    }

    public BigDecimal getAvgWin() {
        return avgWin;
    }

    public BigDecimal getAvgLoss() {
        return avgLoss;
    }

    public BigDecimal getpValue() {
        return pValue;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    @Override
    public String toString() {
        return "MetricsResult{" +
                "sharpeRatio=" + sharpeRatio +
                ", profitFactor=" + profitFactor +
                ", winRate=" + winRate +
                ", maxDrawdown=" + maxDrawdown +
                ", calmarRatio=" + calmarRatio +
                ", totalReturn=" + totalReturn +
                ", annualReturn=" + annualReturn +
                ", totalTrades=" + totalTrades +
                ", winningTrades=" + winningTrades +
                ", losingTrades=" + losingTrades +
                ", avgWin=" + avgWin +
                ", avgLoss=" + avgLoss +
                ", pValue=" + pValue +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
