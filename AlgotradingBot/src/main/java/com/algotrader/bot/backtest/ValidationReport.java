package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.BacktestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DTO representing the validation report for backtest quality gates.
 * Contains overall status, individual gate results, failure reasons, and recommendations.
 */
public class ValidationReport {

    private final BacktestResult.ValidationStatus overallStatus;
    private final List<GateResult> gateResults;
    private final List<String> failureReasons;
    private final List<String> recommendations;

    public ValidationReport(BacktestResult.ValidationStatus overallStatus,
                           List<GateResult> gateResults,
                           List<String> failureReasons,
                           List<String> recommendations) {
        this.overallStatus = overallStatus;
        this.gateResults = new ArrayList<>(gateResults);
        this.failureReasons = new ArrayList<>(failureReasons);
        this.recommendations = new ArrayList<>(recommendations);
    }

    public BacktestResult.ValidationStatus getOverallStatus() {
        return overallStatus;
    }

    public List<GateResult> getGateResults() {
        return Collections.unmodifiableList(gateResults);
    }

    public List<String> getFailureReasons() {
        return Collections.unmodifiableList(failureReasons);
    }

    public List<String> getRecommendations() {
        return Collections.unmodifiableList(recommendations);
    }

    /**
     * Individual quality gate result
     */
    public static class GateResult {
        private final String gateName;
        private final boolean passed;
        private final String actualValue;
        private final String expectedValue;

        public GateResult(String gateName, boolean passed, String actualValue, String expectedValue) {
            this.gateName = gateName;
            this.passed = passed;
            this.actualValue = actualValue;
            this.expectedValue = expectedValue;
        }

        public String getGateName() {
            return gateName;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getActualValue() {
            return actualValue;
        }

        public String getExpectedValue() {
            return expectedValue;
        }

        @Override
        public String toString() {
            return "GateResult{" +
                    "gateName='" + gateName + '\'' +
                    ", passed=" + passed +
                    ", actualValue='" + actualValue + '\'' +
                    ", expectedValue='" + expectedValue + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ValidationReport{" +
                "overallStatus=" + overallStatus +
                ", gateResults=" + gateResults +
                ", failureReasons=" + failureReasons +
                ", recommendations=" + recommendations +
                '}';
    }
}
