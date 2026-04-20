package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.BacktestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validator for backtest results against quality gate requirements.
 * ALL gates must pass for PRODUCTION_READY status.
 * 
 * Quality Gate Requirements:
 * - Sharpe Ratio > 1.0
 * - Profit Factor > 1.5
 * - Win Rate 45-55%
 * - Max Drawdown < 25%
 * - Calmar Ratio > 1.0
 * - Monte Carlo confidence >= 95%
 * - Statistical significance (p-value < 0.05)
 * - Minimum 30 trades
 */
public class BacktestValidator {

    private static final Logger logger = LoggerFactory.getLogger(BacktestValidator.class);

    // Quality gate thresholds
    private static final BigDecimal MIN_SHARPE_RATIO = new BigDecimal("1.0");
    private static final BigDecimal MIN_PROFIT_FACTOR = new BigDecimal("1.5");
    private static final BigDecimal MIN_WIN_RATE = new BigDecimal("0.45");
    private static final BigDecimal MAX_WIN_RATE = new BigDecimal("0.55");
    private static final BigDecimal MAX_DRAWDOWN = new BigDecimal("0.25");
    private static final BigDecimal MIN_CALMAR_RATIO = new BigDecimal("1.0");
    private static final BigDecimal MIN_MONTE_CARLO_CONFIDENCE = new BigDecimal("0.95");
    private static final BigDecimal MAX_P_VALUE = new BigDecimal("0.05");
    private static final int MIN_TRADES = 30;

    /**
     * Validate backtest results against all quality gates.
     * 
     * @param metricsResult Calculated backtest metrics
     * @param mcResult Monte Carlo simulation results
     * @return ValidationReport with overall status and individual gate results
     */
    public ValidationReport validate(MetricsResult metricsResult, MonteCarloResult mcResult) {
        logger.info("Starting backtest validation");

        List<ValidationReport.GateResult> gateResults = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Gate 1: Sharpe Ratio > 1.0
        boolean sharpePass = validateSharpeRatio(metricsResult, gateResults, failureReasons, recommendations);

        // Gate 2: Profit Factor > 1.5
        boolean profitFactorPass = validateProfitFactor(metricsResult, gateResults, failureReasons, recommendations);

        // Gate 3: Win Rate 45-55%
        boolean winRatePass = validateWinRate(metricsResult, gateResults, failureReasons, recommendations);

        // Gate 4: Max Drawdown < 25%
        boolean drawdownPass = validateMaxDrawdown(metricsResult, gateResults, failureReasons, recommendations);

        // Gate 5: Calmar Ratio > 1.0
        boolean calmarPass = validateCalmarRatio(metricsResult, gateResults, failureReasons, recommendations);

        // Gate 6: Monte Carlo confidence >= 95%
        boolean monteCarloPass = validateMonteCarloConfidence(mcResult, gateResults, failureReasons, recommendations);

        // Gate 7: Statistical significance (p-value < 0.05)
        boolean pValuePass = validateStatisticalSignificance(metricsResult, gateResults, failureReasons, recommendations);

        // Gate 8: Minimum 30 trades
        boolean minTradesPass = validateMinimumTrades(metricsResult, gateResults, failureReasons, recommendations);

        // Determine overall status
        boolean allPassed = sharpePass && profitFactorPass && winRatePass && drawdownPass &&
                           calmarPass && monteCarloPass && pValuePass && minTradesPass;

        BacktestResult.ValidationStatus overallStatus;
        if (allPassed) {
            overallStatus = BacktestResult.ValidationStatus.PRODUCTION_READY;
            logger.info("Backtest validation PASSED - Strategy is PRODUCTION_READY");
        } else {
            overallStatus = BacktestResult.ValidationStatus.FAILED;
            logger.warn("Backtest validation FAILED - {} gate(s) failed", failureReasons.size());
        }

        return new ValidationReport(overallStatus, gateResults, failureReasons, recommendations);
    }

    private boolean validateSharpeRatio(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                       List<String> failureReasons, List<String> recommendations) {
        BigDecimal sharpe = metrics.getSharpeRatio();
        boolean passed = sharpe.compareTo(MIN_SHARPE_RATIO) > 0;

        gateResults.add(new ValidationReport.GateResult(
            "Sharpe Ratio",
            passed,
            sharpe.toPlainString(),
            "> " + MIN_SHARPE_RATIO.toPlainString()
        ));

        if (!passed) {
            failureReasons.add("Sharpe Ratio " + sharpe.toPlainString() + " is below minimum " + MIN_SHARPE_RATIO.toPlainString());
            recommendations.add("Improve risk-adjusted returns by: reducing position sizes, tightening stop-losses, or filtering low-quality signals");
        }

        return passed;
    }

    private boolean validateProfitFactor(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                        List<String> failureReasons, List<String> recommendations) {
        BigDecimal profitFactor = metrics.getProfitFactor();
        boolean passed = profitFactor.compareTo(MIN_PROFIT_FACTOR) > 0;

        gateResults.add(new ValidationReport.GateResult(
            "Profit Factor",
            passed,
            profitFactor.toPlainString(),
            "> " + MIN_PROFIT_FACTOR.toPlainString()
        ));

        if (!passed) {
            failureReasons.add("Profit Factor " + profitFactor.toPlainString() + " is below minimum " + MIN_PROFIT_FACTOR.toPlainString());
            recommendations.add("Increase profit factor by: letting winners run longer, cutting losses faster, or improving entry timing");
        }

        return passed;
    }

    private boolean validateWinRate(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                   List<String> failureReasons, List<String> recommendations) {
        BigDecimal winRate = metrics.getWinRate();
        boolean passed = winRate.compareTo(MIN_WIN_RATE) >= 0 && winRate.compareTo(MAX_WIN_RATE) <= 0;

        gateResults.add(new ValidationReport.GateResult(
            "Win Rate",
            passed,
            String.format("%.2f%%", winRate.multiply(new BigDecimal("100"))),
            MIN_WIN_RATE.multiply(new BigDecimal("100")).toPlainString() + "% - " + 
            MAX_WIN_RATE.multiply(new BigDecimal("100")).toPlainString() + "%"
        ));

        if (!passed) {
            if (winRate.compareTo(MIN_WIN_RATE) < 0) {
                failureReasons.add("Win Rate " + String.format("%.2f%%", winRate.multiply(new BigDecimal("100"))) + 
                                 " is below minimum 45%");
                recommendations.add("Increase win rate by: improving entry signals, avoiding choppy markets, or using tighter filters");
            } else {
                failureReasons.add("Win Rate " + String.format("%.2f%%", winRate.multiply(new BigDecimal("100"))) + 
                                 " is above maximum 55%");
                recommendations.add("Win rate too high may indicate curve-fitting or unrealistic assumptions - review strategy logic");
            }
        }

        return passed;
    }

    private boolean validateMaxDrawdown(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                       List<String> failureReasons, List<String> recommendations) {
        BigDecimal maxDrawdown = metrics.getMaxDrawdown();
        boolean passed = maxDrawdown.compareTo(MAX_DRAWDOWN) < 0;

        gateResults.add(new ValidationReport.GateResult(
            "Max Drawdown",
            passed,
            String.format("%.2f%%", maxDrawdown.multiply(new BigDecimal("100"))),
            "< " + MAX_DRAWDOWN.multiply(new BigDecimal("100")).toPlainString() + "%"
        ));

        if (!passed) {
            failureReasons.add("Max Drawdown " + String.format("%.2f%%", maxDrawdown.multiply(new BigDecimal("100"))) + 
                             " exceeds maximum 25%");
            recommendations.add("Reduce drawdown by: implementing stricter position sizing, adding circuit breakers, or diversifying across symbols");
        }

        return passed;
    }

    private boolean validateCalmarRatio(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                       List<String> failureReasons, List<String> recommendations) {
        BigDecimal calmar = metrics.getCalmarRatio();
        boolean passed = calmar.compareTo(MIN_CALMAR_RATIO) > 0;

        gateResults.add(new ValidationReport.GateResult(
            "Calmar Ratio",
            passed,
            calmar.toPlainString(),
            "> " + MIN_CALMAR_RATIO.toPlainString()
        ));

        if (!passed) {
            failureReasons.add("Calmar Ratio " + calmar.toPlainString() + " is below minimum " + MIN_CALMAR_RATIO.toPlainString());
            recommendations.add("Improve Calmar ratio by: increasing returns relative to drawdown, or reducing maximum drawdown");
        }

        return passed;
    }

    private boolean validateMonteCarloConfidence(MonteCarloResult mcResult, List<ValidationReport.GateResult> gateResults,
                                                List<String> failureReasons, List<String> recommendations) {
        BigDecimal confidence = mcResult.getProfitablePercentage();
        boolean passed = confidence.compareTo(MIN_MONTE_CARLO_CONFIDENCE) >= 0;

        gateResults.add(new ValidationReport.GateResult(
            "Monte Carlo Confidence",
            passed,
            String.format("%.2f%%", confidence.multiply(new BigDecimal("100"))),
            ">= " + MIN_MONTE_CARLO_CONFIDENCE.multiply(new BigDecimal("100")).toPlainString() + "%"
        ));

        if (!passed) {
            failureReasons.add("Monte Carlo confidence " + String.format("%.2f%%", confidence.multiply(new BigDecimal("100"))) + 
                             " is below minimum 95%");
            recommendations.add("Strategy lacks robustness - results are too dependent on trade order. Consider improving consistency of returns");
        }

        return passed;
    }

    private boolean validateStatisticalSignificance(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                                   List<String> failureReasons, List<String> recommendations) {
        BigDecimal pValue = metrics.getpValue();
        boolean passed = pValue.compareTo(MAX_P_VALUE) < 0;

        gateResults.add(new ValidationReport.GateResult(
            "Statistical Significance",
            passed,
            "p-value = " + pValue.toPlainString(),
            "p-value < " + MAX_P_VALUE.toPlainString()
        ));

        if (!passed) {
            failureReasons.add("P-value " + pValue.toPlainString() + " exceeds maximum " + MAX_P_VALUE.toPlainString());
            recommendations.add("Results not statistically significant - may be due to random chance. Increase sample size or improve strategy edge");
        }

        return passed;
    }

    private boolean validateMinimumTrades(MetricsResult metrics, List<ValidationReport.GateResult> gateResults,
                                         List<String> failureReasons, List<String> recommendations) {
        int totalTrades = metrics.getTotalTrades();
        boolean passed = totalTrades >= MIN_TRADES;

        gateResults.add(new ValidationReport.GateResult(
            "Minimum Trades",
            passed,
            String.valueOf(totalTrades),
            ">= " + MIN_TRADES
        ));

        if (!passed) {
            failureReasons.add("Total trades " + totalTrades + " is below minimum " + MIN_TRADES);
            recommendations.add("Insufficient sample size for statistical validity. Extend backtest period or increase trading frequency");
        }

        return passed;
    }
}
