package com.algotrader.bot.backtest;

import java.math.BigDecimal;

/**
 * DTO representing the results of a Monte Carlo simulation.
 * Contains aggregated statistics across all iterations.
 */
public class MonteCarloResult {
    private final int totalIterations;
    private final int profitableIterations;
    private final BigDecimal profitablePercentage;
    private final BigDecimal averageFinalBalance;
    private final BigDecimal worstCaseDrawdown;
    private final BigDecimal percentile5th;
    private final BigDecimal percentile50th;
    private final BigDecimal percentile95th;
    private final BigDecimal averageSharpeRatio;

    public MonteCarloResult(int totalIterations, int profitableIterations,
                           BigDecimal profitablePercentage, BigDecimal averageFinalBalance,
                           BigDecimal worstCaseDrawdown, BigDecimal percentile5th,
                           BigDecimal percentile50th, BigDecimal percentile95th,
                           BigDecimal averageSharpeRatio) {
        this.totalIterations = totalIterations;
        this.profitableIterations = profitableIterations;
        this.profitablePercentage = profitablePercentage;
        this.averageFinalBalance = averageFinalBalance;
        this.worstCaseDrawdown = worstCaseDrawdown;
        this.percentile5th = percentile5th;
        this.percentile50th = percentile50th;
        this.percentile95th = percentile95th;
        this.averageSharpeRatio = averageSharpeRatio;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public int getProfitableIterations() {
        return profitableIterations;
    }

    public BigDecimal getProfitablePercentage() {
        return profitablePercentage;
    }

    public BigDecimal getAverageFinalBalance() {
        return averageFinalBalance;
    }

    public BigDecimal getWorstCaseDrawdown() {
        return worstCaseDrawdown;
    }

    public BigDecimal getPercentile5th() {
        return percentile5th;
    }

    public BigDecimal getPercentile50th() {
        return percentile50th;
    }

    public BigDecimal getPercentile95th() {
        return percentile95th;
    }

    public BigDecimal getAverageSharpeRatio() {
        return averageSharpeRatio;
    }

    @Override
    public String toString() {
        return "MonteCarloResult{" +
                "totalIterations=" + totalIterations +
                ", profitableIterations=" + profitableIterations +
                ", profitablePercentage=" + profitablePercentage +
                ", averageFinalBalance=" + averageFinalBalance +
                ", worstCaseDrawdown=" + worstCaseDrawdown +
                ", percentile5th=" + percentile5th +
                ", percentile50th=" + percentile50th +
                ", percentile95th=" + percentile95th +
                ", averageSharpeRatio=" + averageSharpeRatio +
                '}';
    }
}
