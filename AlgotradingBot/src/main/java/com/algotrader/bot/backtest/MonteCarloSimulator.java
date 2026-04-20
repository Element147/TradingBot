package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Monte Carlo simulator for testing strategy robustness.
 * Shuffles trade order randomly across multiple iterations to assess
 * the impact of trade sequence on overall performance.
 */
public class MonteCarloSimulator {
    private static final Logger logger = LoggerFactory.getLogger(MonteCarloSimulator.class);
    private static final int DEFAULT_ITERATIONS = 1000;
    private static final int SCALE = 8;

    /**
     * Run Monte Carlo simulation with default 1000 iterations.
     *
     * @param trades List of completed trades
     * @param initialBalance Starting account balance
     * @return MonteCarloResult with aggregated statistics
     */
    public MonteCarloResult simulate(List<Trade> trades, BigDecimal initialBalance) {
        return simulate(trades, initialBalance, DEFAULT_ITERATIONS, null);
    }

    /**
     * Run Monte Carlo simulation with specified number of iterations.
     *
     * @param trades List of completed trades
     * @param initialBalance Starting account balance
     * @param iterations Number of simulation iterations
     * @return MonteCarloResult with aggregated statistics
     */
    public MonteCarloResult simulate(List<Trade> trades, BigDecimal initialBalance, int iterations) {
        return simulate(trades, initialBalance, iterations, null);
    }

    /**
     * Run Monte Carlo simulation with specified iterations and random seed.
     *
     * @param trades List of completed trades
     * @param initialBalance Starting account balance
     * @param iterations Number of simulation iterations
     * @param seed Random seed for reproducibility (null for random)
     * @return MonteCarloResult with aggregated statistics
     */
    public MonteCarloResult simulate(List<Trade> trades, BigDecimal initialBalance,
                                     int iterations, Long seed) {
        if (trades == null || trades.isEmpty()) {
            logger.warn("No trades provided for Monte Carlo simulation");
            return createZeroResult(iterations);
        }

        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial balance must be positive");
        }

        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be at least 1");
        }

        logger.info("Starting Monte Carlo simulation: {} trades, {} iterations, seed={}",
                trades.size(), iterations, seed);

        Random random = seed != null ? new Random(seed) : new Random();
        List<BigDecimal> finalBalances = new ArrayList<>(iterations);
        List<BigDecimal> maxDrawdowns = new ArrayList<>(iterations);
        List<BigDecimal> sharpeRatios = new ArrayList<>(iterations);
        int profitableCount = 0;

        for (int i = 0; i < iterations; i++) {
            // Shuffle trades randomly
            List<Trade> shuffledTrades = new ArrayList<>(trades);
            Collections.shuffle(shuffledTrades, random);

            // Simulate equity curve with shuffled trades
            SimulationResult result = simulateEquityCurve(shuffledTrades, initialBalance);

            finalBalances.add(result.finalBalance);
            maxDrawdowns.add(result.maxDrawdown);
            sharpeRatios.add(result.sharpeRatio);

            if (result.finalBalance.compareTo(initialBalance) > 0) {
                profitableCount++;
            }
        }

        // Calculate aggregated statistics
        BigDecimal profitablePercentage = BigDecimal.valueOf(profitableCount)
                .divide(BigDecimal.valueOf(iterations), SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal avgFinalBalance = calculateAverage(finalBalances);
        BigDecimal worstDrawdown = maxDrawdowns.stream()
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal avgSharpe = calculateAverage(sharpeRatios);

        // Calculate percentiles
        Collections.sort(finalBalances);
        BigDecimal percentile5th = calculatePercentile(finalBalances, 5);
        BigDecimal percentile50th = calculatePercentile(finalBalances, 50);
        BigDecimal percentile95th = calculatePercentile(finalBalances, 95);

        logger.info("Monte Carlo simulation complete: {}% profitable, avg balance={}",
                profitablePercentage, avgFinalBalance);

        return new MonteCarloResult(
                iterations,
                profitableCount,
                profitablePercentage,
                avgFinalBalance,
                worstDrawdown,
                percentile5th,
                percentile50th,
                percentile95th,
                avgSharpe
        );
    }

    /**
     * Simulate equity curve for a shuffled sequence of trades.
     */
    private SimulationResult simulateEquityCurve(List<Trade> trades, BigDecimal initialBalance) {
        BigDecimal balance = initialBalance;
        BigDecimal peakBalance = initialBalance;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        List<BigDecimal> equityCurve = new ArrayList<>();
        equityCurve.add(initialBalance);

        for (Trade trade : trades) {
            if (trade.getPnl() != null) {
                balance = balance.add(trade.getPnl());
                equityCurve.add(balance);

                // Update peak and drawdown
                if (balance.compareTo(peakBalance) > 0) {
                    peakBalance = balance;
                }

                BigDecimal drawdown = peakBalance.subtract(balance)
                        .divide(peakBalance, SCALE, RoundingMode.HALF_UP)
                        .abs();

                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        // Calculate Sharpe ratio for this iteration
        BigDecimal sharpeRatio = calculateSimpleSharpeRatio(trades, initialBalance);

        return new SimulationResult(balance, maxDrawdown, sharpeRatio);
    }

    /**
     * Calculate simplified Sharpe ratio for a trade sequence.
     */
    private BigDecimal calculateSimpleSharpeRatio(List<Trade> trades, BigDecimal initialBalance) {
        List<BigDecimal> returns = trades.stream()
                .filter(t -> t.getPnl() != null)
                .map(t -> t.getPnl().divide(initialBalance, SCALE, RoundingMode.HALF_UP))
                .collect(Collectors.toList());

        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgReturn = calculateAverage(returns);
        BigDecimal stdDev = calculateStandardDeviation(returns, avgReturn);

        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return avgReturn.divide(stdDev, SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(Math.sqrt(365)));
    }

    /**
     * Calculate average of a list of BigDecimal values.
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate standard deviation of a list of BigDecimal values.
     */
    private BigDecimal calculateStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumSquaredDiff = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(values.size() - 1), SCALE, RoundingMode.HALF_UP);

        return sqrt(variance);
    }

    /**
     * Calculate square root using Newton's method.
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal x = value;
        BigDecimal two = BigDecimal.valueOf(2);

        for (int i = 0; i < 50; i++) {
            BigDecimal nextX = x.add(value.divide(x, SCALE, RoundingMode.HALF_UP))
                    .divide(two, SCALE, RoundingMode.HALF_UP);

            if (nextX.subtract(x).abs().compareTo(BigDecimal.valueOf(0.00000001)) < 0) {
                break;
            }
            x = nextX;
        }

        return x;
    }

    /**
     * Calculate percentile from sorted list of values.
     */
    private BigDecimal calculatePercentile(List<BigDecimal> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return BigDecimal.ZERO;
        }

        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }

        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));

        return sortedValues.get(index);
    }

    /**
     * Create zero result for edge cases.
     */
    private MonteCarloResult createZeroResult(int iterations) {
        return new MonteCarloResult(
                iterations,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    /**
     * Internal class to hold simulation results for one iteration.
     */
    private static class SimulationResult {
        final BigDecimal finalBalance;
        final BigDecimal maxDrawdown;
        final BigDecimal sharpeRatio;

        SimulationResult(BigDecimal finalBalance, BigDecimal maxDrawdown, BigDecimal sharpeRatio) {
            this.finalBalance = finalBalance;
            this.maxDrawdown = maxDrawdown;
            this.sharpeRatio = sharpeRatio;
        }
    }
}
