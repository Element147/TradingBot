package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.Trade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calculator for backtest performance metrics.
 * All calculations use BigDecimal for financial precision.
 * Handles edge cases: zero trades, all wins, all losses, division by zero.
 */
@Component
public class BacktestMetrics {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal SQRT_365 = new BigDecimal("19.1049731745428"); // sqrt(365)
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    /**
     * Calculate all performance metrics from trade history and equity curve.
     *
     * @param trades List of completed trades with PnL
     * @param equityCurve List of equity values over time (for drawdown calculation)
     * @param initialBalance Starting account balance
     * @param startDate Backtest start date
     * @param endDate Backtest end date
     * @return MetricsResult containing all calculated metrics
     */
    public MetricsResult calculateMetrics(List<Trade> trades, List<BigDecimal> equityCurve,
                                          BigDecimal initialBalance, LocalDateTime startDate,
                                          LocalDateTime endDate) {
        
        // Handle edge case: no trades
        if (trades == null || trades.isEmpty()) {
            return createZeroMetrics(startDate, endDate);
        }

        // Calculate basic trade statistics
        int totalTrades = trades.size();
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal sumWinningTrades = BigDecimal.ZERO;
        BigDecimal sumLosingTrades = BigDecimal.ZERO;
        BigDecimal totalPnL = BigDecimal.ZERO;

        for (Trade trade : trades) {
            BigDecimal pnl = trade.getPnl();
            if (pnl == null) {
                continue; // Skip incomplete trades
            }

            totalPnL = totalPnL.add(pnl);

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                sumWinningTrades = sumWinningTrades.add(pnl);
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                losingTrades++;
                sumLosingTrades = sumLosingTrades.add(pnl);
            }
        }

        // Calculate final balance
        BigDecimal finalBalance = initialBalance.add(totalPnL);

        // Calculate individual metrics
        BigDecimal sharpeRatio = calculateSharpeRatio(trades, initialBalance);
        BigDecimal profitFactor = calculateProfitFactor(sumWinningTrades, sumLosingTrades);
        BigDecimal winRate = calculateWinRate(winningTrades, totalTrades);
        BigDecimal maxDrawdown = calculateMaxDrawdown(equityCurve);
        BigDecimal totalReturn = calculateTotalReturn(initialBalance, finalBalance);
        BigDecimal annualReturn = calculateAnnualReturn(totalReturn, startDate, endDate);
        BigDecimal calmarRatio = calculateCalmarRatio(annualReturn, maxDrawdown);
        BigDecimal avgWin = winningTrades > 0 ? 
            sumWinningTrades.divide(new BigDecimal(winningTrades), MC) : BigDecimal.ZERO;
        BigDecimal avgLoss = losingTrades > 0 ? 
            sumLosingTrades.divide(new BigDecimal(losingTrades), MC) : BigDecimal.ZERO;
        BigDecimal pValue = calculatePValue(trades);

        return new MetricsResult(
            sharpeRatio,
            profitFactor,
            winRate,
            maxDrawdown,
            calmarRatio,
            totalReturn,
            annualReturn,
            totalTrades,
            winningTrades,
            losingTrades,
            avgWin,
            avgLoss,
            pValue,
            startDate,
            endDate
        );
    }

    /**
     * Calculate Sharpe Ratio: (avgDailyReturn - riskFreeRate) / stdDevDailyReturns * sqrt(365)
     * Uses risk-free rate = 0 (conservative approach)
     */
    private BigDecimal calculateSharpeRatio(List<Trade> trades, BigDecimal initialBalance) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate daily returns from trades
        BigDecimal sumReturns = BigDecimal.ZERO;
        BigDecimal sumSquaredReturns = BigDecimal.ZERO;
        int count = 0;

        BigDecimal currentBalance = initialBalance;
        for (Trade trade : trades) {
            BigDecimal pnl = trade.getPnl();
            if (pnl == null) {
                continue;
            }

            // Calculate return as percentage of current balance
            BigDecimal dailyReturn = pnl.divide(currentBalance, MC);
            sumReturns = sumReturns.add(dailyReturn);
            sumSquaredReturns = sumSquaredReturns.add(dailyReturn.multiply(dailyReturn));
            currentBalance = currentBalance.add(pnl);
            count++;
        }

        if (count == 0) {
            return BigDecimal.ZERO;
        }

        // Calculate average return
        BigDecimal avgReturn = sumReturns.divide(new BigDecimal(count), MC);

        // Calculate standard deviation
        BigDecimal variance = sumSquaredReturns.divide(new BigDecimal(count), MC)
            .subtract(avgReturn.multiply(avgReturn));

        // Handle edge case: zero variance (all returns identical)
        if (variance.compareTo(BigDecimal.ZERO) <= 0) {
            return avgReturn.compareTo(BigDecimal.ZERO) > 0 ? 
                new BigDecimal("999.99") : BigDecimal.ZERO;
        }

        BigDecimal stdDev = sqrt(variance);

        // Sharpe = avgReturn / stdDev * sqrt(365)
        // Using risk-free rate = 0
        return avgReturn.divide(stdDev, MC).multiply(SQRT_365);
    }

    /**
     * Calculate Profit Factor: sumWinningTrades / abs(sumLosingTrades)
     */
    private BigDecimal calculateProfitFactor(BigDecimal sumWinningTrades, BigDecimal sumLosingTrades) {
        // Edge case: no losing trades (all wins)
        if (sumLosingTrades.compareTo(BigDecimal.ZERO) == 0) {
            return sumWinningTrades.compareTo(BigDecimal.ZERO) > 0 ? 
                new BigDecimal("999.99") : BigDecimal.ZERO;
        }

        // Edge case: no winning trades (all losses)
        if (sumWinningTrades.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return sumWinningTrades.divide(sumLosingTrades.abs(), MC);
    }

    /**
     * Calculate Win Rate: winningTrades / totalTrades
     */
    private BigDecimal calculateWinRate(int winningTrades, int totalTrades) {
        if (totalTrades == 0) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(winningTrades)
            .divide(new BigDecimal(totalTrades), MC);
    }

    /**
     * Calculate Max Drawdown: (lowestEquity - peakEquity) / peakEquity
     * Tracks peak equity continuously and finds maximum drawdown.
     */
    private BigDecimal calculateMaxDrawdown(List<BigDecimal> equityCurve) {
        if (equityCurve == null || equityCurve.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal peakEquity = equityCurve.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (BigDecimal equity : equityCurve) {
            // Update peak if current equity is higher
            if (equity.compareTo(peakEquity) > 0) {
                peakEquity = equity;
            }

            // Calculate drawdown from peak
            BigDecimal drawdown = equity.subtract(peakEquity).divide(peakEquity, MC);

            // Update max drawdown if current is worse
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown.abs();
    }

    /**
     * Calculate Total Return: (finalBalance - initialBalance) / initialBalance
     */
    private BigDecimal calculateTotalReturn(BigDecimal initialBalance, BigDecimal finalBalance) {
        if (initialBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return finalBalance.subtract(initialBalance).divide(initialBalance, MC);
    }

    /**
     * Calculate Annual Return: totalReturn / (days / 365)
     */
    private BigDecimal calculateAnnualReturn(BigDecimal totalReturn, LocalDateTime startDate, 
                                            LocalDateTime endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        
        // Edge case: less than 1 day
        if (days == 0) {
            return totalReturn;
        }

        BigDecimal years = new BigDecimal(days).divide(DAYS_PER_YEAR, MC);
        
        // Edge case: less than 1 year, annualize proportionally
        if (years.compareTo(BigDecimal.ZERO) == 0) {
            return totalReturn;
        }

        return totalReturn.divide(years, MC);
    }

    /**
     * Calculate Calmar Ratio: annualReturn / abs(maxDrawdown)
     */
    private BigDecimal calculateCalmarRatio(BigDecimal annualReturn, BigDecimal maxDrawdown) {
        // Edge case: zero drawdown (perfect performance)
        if (maxDrawdown.compareTo(BigDecimal.ZERO) == 0) {
            return annualReturn.compareTo(BigDecimal.ZERO) > 0 ? 
                new BigDecimal("999.99") : BigDecimal.ZERO;
        }

        return annualReturn.divide(maxDrawdown, MC);
    }

    /**
     * Calculate statistical significance (p-value) using t-test.
     * Tests if average trade PnL is significantly different from zero.
     */
    private BigDecimal calculatePValue(List<Trade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ONE; // Not significant
        }

        // Calculate mean and standard deviation of PnL
        BigDecimal sumPnL = BigDecimal.ZERO;
        int count = 0;

        for (Trade trade : trades) {
            BigDecimal pnl = trade.getPnl();
            if (pnl != null) {
                sumPnL = sumPnL.add(pnl);
                count++;
            }
        }

        if (count == 0) {
            return BigDecimal.ONE;
        }

        if (count < 2) {
            return BigDecimal.ONE;
        }

        BigDecimal mean = sumPnL.divide(new BigDecimal(count), MC);

        // Calculate standard deviation
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (Trade trade : trades) {
            BigDecimal pnl = trade.getPnl();
            if (pnl != null) {
                BigDecimal diff = pnl.subtract(mean);
                sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
            }
        }

        BigDecimal variance = sumSquaredDiff.divide(new BigDecimal(count - 1), MC);
        BigDecimal stdDev = sqrt(variance);

        // Calculate t-statistic: mean / (stdDev / sqrt(n))
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return mean.compareTo(BigDecimal.ZERO) == 0 ? 
                BigDecimal.ONE : BigDecimal.ZERO;
        }

        BigDecimal sqrtN = sqrt(new BigDecimal(count));
        BigDecimal standardError = stdDev.divide(sqrtN, MC);
        BigDecimal tStatistic = mean.divide(standardError, MC).abs();

        // Simplified p-value approximation
        // For t > 2.0 with reasonable sample size, p < 0.05
        // For t > 3.0, p < 0.01
        if (tStatistic.compareTo(new BigDecimal("3.0")) >= 0) {
            return new BigDecimal("0.01");
        } else if (tStatistic.compareTo(new BigDecimal("2.0")) >= 0) {
            return new BigDecimal("0.04");
        } else if (tStatistic.compareTo(new BigDecimal("1.5")) >= 0) {
            return new BigDecimal("0.10");
        } else {
            return new BigDecimal("0.20");
        }
    }

    /**
     * Create zero metrics result for edge case of no trades.
     */
    private MetricsResult createZeroMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        return new MetricsResult(
            BigDecimal.ZERO,  // sharpeRatio
            BigDecimal.ZERO,  // profitFactor
            BigDecimal.ZERO,  // winRate
            BigDecimal.ZERO,  // maxDrawdown
            BigDecimal.ZERO,  // calmarRatio
            BigDecimal.ZERO,  // totalReturn
            BigDecimal.ZERO,  // annualReturn
            0,                // totalTrades
            0,                // winningTrades
            0,                // losingTrades
            BigDecimal.ZERO,  // avgWin
            BigDecimal.ZERO,  // avgLoss
            BigDecimal.ONE,   // pValue (not significant)
            startDate,
            endDate
        );
    }

    /**
     * Calculate square root using Newton's method for BigDecimal.
     * Precision matches MathContext.
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ArithmeticException("Cannot calculate square root of negative number");
        }

        // Newton's method: x_new = (x + value/x) / 2
        BigDecimal x = value;
        BigDecimal lastX;

        do {
            lastX = x;
            x = value.divide(x, MC).add(x).divide(new BigDecimal("2"), MC);
        } while (x.subtract(lastX).abs().compareTo(new BigDecimal("0.0000000001")) > 0);

        return x;
    }
}
