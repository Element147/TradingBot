package com.algotrader.bot.backtest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Component
public class BacktestSimulationMetricsCalculator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BacktestSimulationResult calculate(
        BigDecimal finalBalance,
        List<BigDecimal> tradeReturns,
        List<BigDecimal> equityCurve,
        int winningTrades
    ) {
        int totalTrades = tradeReturns.size();
        BigDecimal winRatePercent = totalTrades == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(winningTrades)
                .multiply(HUNDRED, MC)
                .divide(BigDecimal.valueOf(totalTrades), 2, RoundingMode.HALF_UP);

        BigDecimal profitFactor = calculateProfitFactor(tradeReturns).setScale(4, RoundingMode.HALF_UP);
        BigDecimal maxDrawdownPercent = calculateMaxDrawdownPercent(equityCurve).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sharpeRatio = calculateSharpeRatio(tradeReturns).setScale(4, RoundingMode.HALF_UP);

        return new BacktestSimulationResult(
            finalBalance.setScale(8, RoundingMode.HALF_UP),
            sharpeRatio,
            profitFactor,
            winRatePercent,
            maxDrawdownPercent,
            totalTrades,
            List.of(),
            List.of()
        );
    }

    private BigDecimal calculateProfitFactor(List<BigDecimal> tradeReturns) {
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (BigDecimal tradeReturn : tradeReturns) {
            if (tradeReturn.compareTo(BigDecimal.ZERO) > 0) {
                gains = gains.add(tradeReturn, MC);
            } else if (tradeReturn.compareTo(BigDecimal.ZERO) < 0) {
                losses = losses.add(tradeReturn.abs(), MC);
            }
        }

        if (losses.compareTo(BigDecimal.ZERO) == 0) {
            return gains.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("999.99") : BigDecimal.ZERO;
        }

        return gains.divide(losses, MC);
    }

    private BigDecimal calculateMaxDrawdownPercent(List<BigDecimal> equityCurve) {
        BigDecimal peak = equityCurve.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (BigDecimal equity : equityCurve) {
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }

            BigDecimal drawdown = peak.subtract(equity, MC)
                .divide(peak, MC)
                .multiply(HUNDRED, MC);

            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    private BigDecimal calculateSharpeRatio(List<BigDecimal> tradeReturns) {
        if (tradeReturns.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal mean = tradeReturns.stream()
            .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MC))
            .divide(BigDecimal.valueOf(tradeReturns.size()), MC);

        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal tradeReturn : tradeReturns) {
            BigDecimal delta = tradeReturn.subtract(mean, MC);
            variance = variance.add(delta.multiply(delta, MC), MC);
        }

        variance = variance.divide(BigDecimal.valueOf(tradeReturns.size() - 1), MC);
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal standardDeviation = sqrt(variance);
        return mean.divide(standardDeviation, MC).multiply(new BigDecimal("15.874507866"), MC);
    }

    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal estimate = value;
        BigDecimal previous;
        do {
            previous = estimate;
            estimate = estimate.add(value.divide(estimate, MC), MC).divide(BigDecimal.valueOf(2), MC);
        } while (estimate.subtract(previous).abs().compareTo(new BigDecimal("0.00000001")) > 0);

        return estimate;
    }
}
