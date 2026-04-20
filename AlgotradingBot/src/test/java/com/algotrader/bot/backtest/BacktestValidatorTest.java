package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.BacktestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BacktestValidator.
 * Tests all quality gate validation scenarios and edge cases.
 */
class BacktestValidatorTest {

    private BacktestValidator validator;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @BeforeEach
    void setUp() {
        validator = new BacktestValidator();
        startDate = LocalDateTime.of(2024, 1, 1, 0, 0);
        endDate = LocalDateTime.of(2024, 12, 31, 23, 59);
    }

    /**
     * Test 1: All gates pass → PRODUCTION_READY
     */
    @Test
    void testAllGatesPass_ProductionReady() {
        // Arrange - Create metrics that pass all gates
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),    // Sharpe > 1.0 ✓
            new BigDecimal("2.0"),    // Profit Factor > 1.5 ✓
            new BigDecimal("0.50"),   // Win Rate 50% (45-55%) ✓
            new BigDecimal("0.20"),   // Max Drawdown 20% < 25% ✓
            new BigDecimal("1.5"),    // Calmar > 1.0 ✓
            new BigDecimal("0.04"),   // p-value < 0.05 ✓
            50                        // Total trades >= 30 ✓
        );

        MonteCarloResult mcResult = createMonteCarloResult(
            new BigDecimal("0.96")    // 96% confidence >= 95% ✓
        );

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.PRODUCTION_READY, report.getOverallStatus());
        assertEquals(8, report.getGateResults().size());
        assertTrue(report.getFailureReasons().isEmpty());
        assertTrue(report.getRecommendations().isEmpty());

        // Verify all gates passed
        for (ValidationReport.GateResult gate : report.getGateResults()) {
            assertTrue(gate.isPassed(), "Gate " + gate.getGateName() + " should pass");
        }
    }

    /**
     * Test 2: Sharpe < 1.0 → FAILED with reason
     */
    @Test
    void testSharpeRatioFails() {
        // Arrange - Sharpe ratio below threshold
        MetricsResult metrics = createMetrics(
            new BigDecimal("0.8"),    // Sharpe < 1.0 ✗
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertEquals(1, report.getFailureReasons().size());
        assertTrue(report.getFailureReasons().get(0).contains("Sharpe Ratio"));
        assertTrue(report.getFailureReasons().get(0).contains("0.8"));
        assertEquals(1, report.getRecommendations().size());
        assertTrue(report.getRecommendations().get(0).contains("risk-adjusted returns"));

        // Verify Sharpe gate failed
        ValidationReport.GateResult sharpeGate = findGate(report, "Sharpe Ratio");
        assertNotNull(sharpeGate);
        assertFalse(sharpeGate.isPassed());
        assertEquals("0.8", sharpeGate.getActualValue());
        assertEquals("> 1.0", sharpeGate.getExpectedValue());
    }

    /**
     * Test 3: Profit Factor < 1.5 → FAILED with reason
     */
    @Test
    void testProfitFactorFails() {
        // Arrange - Profit factor below threshold
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("1.2"),    // Profit Factor < 1.5 ✗
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertEquals(1, report.getFailureReasons().size());
        assertTrue(report.getFailureReasons().get(0).contains("Profit Factor"));
        assertTrue(report.getFailureReasons().get(0).contains("1.2"));
        assertEquals(1, report.getRecommendations().size());
        assertTrue(report.getRecommendations().get(0).contains("profit factor"));

        // Verify Profit Factor gate failed
        ValidationReport.GateResult pfGate = findGate(report, "Profit Factor");
        assertNotNull(pfGate);
        assertFalse(pfGate.isPassed());
    }

    /**
     * Test 4: Win Rate outside 45-55% → FAILED with reason
     */
    @Test
    void testWinRateTooLow() {
        // Arrange - Win rate below 45%
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.40"),   // Win Rate 40% < 45% ✗
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertFalse(report.getFailureReasons().isEmpty());
        String failureReason = String.join(" ", report.getFailureReasons());
        assertTrue(failureReason.contains("Win Rate"));
        assertTrue(failureReason.contains("40") && failureReason.contains("%")); // Handle locale differences
        assertTrue(failureReason.contains("below minimum 45%"));
        assertFalse(report.getRecommendations().isEmpty());
        String recommendations = String.join(" ", report.getRecommendations());
        assertTrue(recommendations.toLowerCase().contains("win rate"));
    }

    @Test
    void testWinRateTooHigh() {
        // Arrange - Win rate above 55%
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.60"),   // Win Rate 60% > 55% ✗
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertFalse(report.getFailureReasons().isEmpty());
        String failureReason = String.join(" ", report.getFailureReasons());
        assertTrue(failureReason.contains("Win Rate"));
        assertTrue(failureReason.contains("60") && failureReason.contains("%")); // Handle locale differences
        assertTrue(failureReason.contains("above maximum 55%"));
        assertFalse(report.getRecommendations().isEmpty());
        String recommendations = String.join(" ", report.getRecommendations());
        assertTrue(recommendations.toLowerCase().contains("curve-fitting"));
    }

    /**
     * Test 5: Max Drawdown > 25% → FAILED with reason
     */
    @Test
    void testMaxDrawdownFails() {
        // Arrange - Max drawdown exceeds threshold
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.30"),   // Max Drawdown 30% > 25% ✗
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertFalse(report.getFailureReasons().isEmpty());
        String failureReason = String.join(" ", report.getFailureReasons());
        assertTrue(failureReason.contains("Max Drawdown"));
        assertTrue(failureReason.contains("30") && failureReason.contains("%")); // Handle locale differences
        assertTrue(failureReason.contains("exceeds maximum 25%"));
        assertFalse(report.getRecommendations().isEmpty());
        String recommendations = String.join(" ", report.getRecommendations());
        assertTrue(recommendations.toLowerCase().contains("drawdown"));
    }

    /**
     * Test 6: Calmar Ratio < 1.0 → FAILED with reason
     */
    @Test
    void testCalmarRatioFails() {
        // Arrange - Calmar ratio below threshold
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("0.8"),    // Calmar < 1.0 ✗
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertTrue(report.getFailureReasons().get(0).contains("Calmar Ratio"));
        assertTrue(report.getFailureReasons().get(0).contains("0.8"));
        assertTrue(report.getRecommendations().get(0).contains("Calmar ratio"));
    }

    /**
     * Test 7: Monte Carlo < 95% → FAILED with reason
     */
    @Test
    void testMonteCarloConfidenceFails() {
        // Arrange - Monte Carlo confidence below threshold
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(
            new BigDecimal("0.90")    // 90% < 95% ✗
        );

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertFalse(report.getFailureReasons().isEmpty());
        String failureReason = String.join(" ", report.getFailureReasons());
        assertTrue(failureReason.contains("Monte Carlo confidence"));
        assertTrue(failureReason.contains("90") && failureReason.contains("%")); // Handle locale differences
        assertFalse(report.getRecommendations().isEmpty());
        String recommendations = String.join(" ", report.getRecommendations());
        assertTrue(recommendations.toLowerCase().contains("robustness"));
    }

    /**
     * Test 8: P-value >= 0.05 → FAILED with reason
     */
    @Test
    void testStatisticalSignificanceFails() {
        // Arrange - P-value not significant
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.08"),   // p-value >= 0.05 ✗
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertTrue(report.getFailureReasons().get(0).contains("P-value"));
        assertTrue(report.getFailureReasons().get(0).contains("0.08"));
        assertTrue(report.getRecommendations().get(0).contains("statistically significant"));
    }

    /**
     * Test 9: Total trades < 30 → FAILED with reason
     */
    @Test
    void testMinimumTradesFails() {
        // Arrange - Insufficient trades
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            20                        // Total trades < 30 ✗
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertTrue(report.getFailureReasons().get(0).contains("Total trades"));
        assertTrue(report.getFailureReasons().get(0).contains("20"));
        assertTrue(report.getRecommendations().get(0).contains("sample size"));
    }

    /**
     * Test 10: Multiple failures → all reasons reported
     */
    @Test
    void testMultipleFailures() {
        // Arrange - Multiple gates fail
        MetricsResult metrics = createMetrics(
            new BigDecimal("0.8"),    // Sharpe fails ✗
            new BigDecimal("1.2"),    // Profit Factor fails ✗
            new BigDecimal("0.40"),   // Win Rate fails ✗
            new BigDecimal("0.30"),   // Max Drawdown fails ✗
            new BigDecimal("0.8"),    // Calmar fails ✗
            new BigDecimal("0.08"),   // p-value fails ✗
            20                        // Min trades fails ✗
        );

        MonteCarloResult mcResult = createMonteCarloResult(
            new BigDecimal("0.90")    // Monte Carlo fails ✗
        );

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertEquals(8, report.getFailureReasons().size());
        assertEquals(8, report.getRecommendations().size());

        // Verify all gates failed
        for (ValidationReport.GateResult gate : report.getGateResults()) {
            assertFalse(gate.isPassed(), "Gate " + gate.getGateName() + " should fail");
        }
    }

    /**
     * Test 11: Boundary values - exactly at thresholds (should pass)
     */
    @Test
    void testBoundaryValuesPass() {
        // Arrange - Values exactly at thresholds
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.01"),   // Sharpe just above 1.0 ✓
            new BigDecimal("1.51"),   // Profit Factor just above 1.5 ✓
            new BigDecimal("0.45"),   // Win Rate exactly 45% ✓
            new BigDecimal("0.2499"), // Max Drawdown just below 25% ✓
            new BigDecimal("1.01"),   // Calmar just above 1.0 ✓
            new BigDecimal("0.0499"), // p-value just below 0.05 ✓
            30                        // Exactly 30 trades ✓
        );

        MonteCarloResult mcResult = createMonteCarloResult(
            new BigDecimal("0.95")    // Exactly 95% ✓
        );

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.PRODUCTION_READY, report.getOverallStatus());
        assertTrue(report.getFailureReasons().isEmpty());
    }

    /**
     * Test 12: Boundary values - just below thresholds (should fail)
     */
    @Test
    void testBoundaryValuesFail() {
        // Arrange - Values just below thresholds
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.0"),    // Sharpe exactly 1.0 (needs >) ✗
            new BigDecimal("1.5"),    // Profit Factor exactly 1.5 (needs >) ✗
            new BigDecimal("0.4499"), // Win Rate just below 45% ✗
            new BigDecimal("0.25"),   // Max Drawdown exactly 25% (needs <) ✗
            new BigDecimal("1.0"),    // Calmar exactly 1.0 (needs >) ✗
            new BigDecimal("0.05"),   // p-value exactly 0.05 (needs <) ✗
            29                        // 29 trades (needs >= 30) ✗
        );

        MonteCarloResult mcResult = createMonteCarloResult(
            new BigDecimal("0.9499")  // Just below 95% ✗
        );

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.FAILED, report.getOverallStatus());
        assertEquals(8, report.getFailureReasons().size());
    }

    /**
     * Test 13: Win rate at upper boundary (55%) should pass
     */
    @Test
    void testWinRateUpperBoundaryPass() {
        // Arrange
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.55"),   // Win Rate exactly 55% ✓
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        assertEquals(BacktestResult.ValidationStatus.PRODUCTION_READY, report.getOverallStatus());
    }

    /**
     * Test 14: Verify gate result structure
     */
    @Test
    void testGateResultStructure() {
        // Arrange
        MetricsResult metrics = createMetrics(
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("1.5"),
            new BigDecimal("0.04"),
            50
        );

        MonteCarloResult mcResult = createMonteCarloResult(new BigDecimal("0.96"));

        // Act
        ValidationReport report = validator.validate(metrics, mcResult);

        // Assert
        List<ValidationReport.GateResult> gates = report.getGateResults();
        assertEquals(8, gates.size());

        // Verify gate names
        assertNotNull(findGate(report, "Sharpe Ratio"));
        assertNotNull(findGate(report, "Profit Factor"));
        assertNotNull(findGate(report, "Win Rate"));
        assertNotNull(findGate(report, "Max Drawdown"));
        assertNotNull(findGate(report, "Calmar Ratio"));
        assertNotNull(findGate(report, "Monte Carlo Confidence"));
        assertNotNull(findGate(report, "Statistical Significance"));
        assertNotNull(findGate(report, "Minimum Trades"));

        // Verify gate structure
        ValidationReport.GateResult sharpeGate = findGate(report, "Sharpe Ratio");
        assertNotNull(sharpeGate.getGateName());
        assertNotNull(sharpeGate.getActualValue());
        assertNotNull(sharpeGate.getExpectedValue());
    }

    // Helper methods

    private MetricsResult createMetrics(BigDecimal sharpe, BigDecimal profitFactor,
                                       BigDecimal winRate, BigDecimal maxDrawdown,
                                       BigDecimal calmar, BigDecimal pValue, int totalTrades) {
        return new MetricsResult(
            sharpe,
            profitFactor,
            winRate,
            maxDrawdown,
            calmar,
            new BigDecimal("0.15"),  // totalReturn
            new BigDecimal("0.18"),  // annualReturn
            totalTrades,
            (int) (totalTrades * winRate.doubleValue()),  // winningTrades
            totalTrades - (int) (totalTrades * winRate.doubleValue()),  // losingTrades
            new BigDecimal("100"),   // avgWin
            new BigDecimal("50"),    // avgLoss
            pValue,
            startDate,
            endDate
        );
    }

    private MonteCarloResult createMonteCarloResult(BigDecimal profitablePercentage) {
        int totalIterations = 1000;
        int profitableIterations = profitablePercentage.multiply(new BigDecimal(totalIterations)).intValue();

        return new MonteCarloResult(
            totalIterations,
            profitableIterations,
            profitablePercentage,
            new BigDecimal("1150.00"),  // averageFinalBalance
            new BigDecimal("0.22"),     // worstCaseDrawdown
            new BigDecimal("1050.00"),  // percentile5th
            new BigDecimal("1150.00"),  // percentile50th
            new BigDecimal("1250.00"),  // percentile95th
            new BigDecimal("1.3")       // averageSharpeRatio
        );
    }

    private ValidationReport.GateResult findGate(ValidationReport report, String gateName) {
        return report.getGateResults().stream()
            .filter(gate -> gate.getGateName().equals(gateName))
            .findFirst()
            .orElse(null);
    }
}
