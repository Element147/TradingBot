package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.BacktestResult;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Walk-forward validation result comparing training and testing performance.
 * Ensures strategy is not overfitted to historical data.
 */
public class WalkForwardResult {
    private final BacktestResult trainingResult;
    private final BacktestResult testingResult;
    private final BigDecimal performanceRatio;
    private final boolean passed;
    
    public WalkForwardResult(BacktestResult trainingResult, BacktestResult testingResult) {
        this.trainingResult = trainingResult;
        this.testingResult = testingResult;
        this.performanceRatio = calculatePerformanceRatio();
        this.passed = performanceRatio.compareTo(new BigDecimal("0.80")) >= 0;
    }
    
    private BigDecimal calculatePerformanceRatio() {
        BigDecimal trainingSharpe = trainingResult.getSharpeRatio();
        BigDecimal testingSharpe = testingResult.getSharpeRatio();
        
        if (trainingSharpe.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return testingSharpe.divide(trainingSharpe, 4, RoundingMode.HALF_UP);
    }
    
    public BacktestResult getTrainingResult() {
        return trainingResult;
    }
    
    public BacktestResult getTestingResult() {
        return testingResult;
    }
    
    public BigDecimal getPerformanceRatio() {
        return performanceRatio;
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    @Override
    public String toString() {
        return String.format(
            "WalkForwardResult{training Sharpe=%.2f, testing Sharpe=%.2f, ratio=%.2f%%, passed=%s}",
            trainingResult.getSharpeRatio(),
            testingResult.getSharpeRatio(),
            performanceRatio.multiply(new BigDecimal("100")),
            passed
        );
    }
}
