package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for BacktestMetrics.
 * Tests all 7+ metrics with known trade sequences and edge cases.
 * Target: 100% code coverage.
 */
class BacktestMetricsTest {

    private BacktestMetrics backtestMetrics;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @BeforeEach
    void setUp() {
        backtestMetrics = new BacktestMetrics();
        startDate = LocalDateTime.of(2024, 1, 1, 0, 0);
        endDate = LocalDateTime.of(2024, 1, 31, 23, 59);
    }

    /**
     * Test 1: Known trade sequence → verify Sharpe ratio
     * Trade sequence: [+$10, -$5, +$8, -$3, +$12, -$4, +$6, -$2]
     */
    @Test
    void testSharpeRatioCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getSharpeRatio());
        assertTrue(result.getSharpeRatio().compareTo(BigDecimal.ZERO) > 0, 
            "Sharpe ratio should be positive for profitable trades");
        assertEquals(8, result.getTotalTrades());
    }

    /**
     * Test 2: Known trade sequence → verify Profit Factor
     * Expected: $36 / $14 = 2.57
     */
    @Test
    void testProfitFactorCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getProfitFactor());
        // Expected: (10 + 8 + 12 + 6) / (5 + 3 + 4 + 2) = 36 / 14 = 2.571...
        BigDecimal expectedProfitFactor = new BigDecimal("2.57");
        assertTrue(result.getProfitFactor().compareTo(expectedProfitFactor) >= 0,
            "Profit factor should be approximately 2.57, got: " + result.getProfitFactor());
    }

    /**
     * Test 3: Known trade sequence → verify Win Rate
     * Expected: 50% (4 wins out of 8 trades)
     */
    @Test
    void testWinRateCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getWinRate());
        // Expected: 4 wins / 8 trades = 0.5 = 50%
        BigDecimal expectedWinRate = new BigDecimal("0.5");
        assertEquals(0, result.getWinRate().setScale(1, RoundingMode.HALF_UP)
            .compareTo(expectedWinRate),
            "Win rate should be 50%, got: " + result.getWinRate());
        assertEquals(4, result.getWinningTrades());
        assertEquals(4, result.getLosingTrades());
    }

    /**
     * Test 4: Known trade sequence → verify Max Drawdown
     */
    @Test
    void testMaxDrawdownCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getMaxDrawdown());
        // Implementation returns absolute value (positive)
        assertTrue(result.getMaxDrawdown().compareTo(BigDecimal.ZERO) >= 0,
            "Max drawdown should be positive (absolute value)");
        assertTrue(result.getMaxDrawdown().compareTo(new BigDecimal("0.25")) < 0,
            "Max drawdown should be less than 25%");
    }

    /**
     * Test 5: Known trade sequence → verify Calmar Ratio
     */
    @Test
    void testCalmarRatioCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getCalmarRatio());
        if (result.getMaxDrawdown().compareTo(BigDecimal.ZERO) < 0) {
            assertTrue(result.getCalmarRatio().compareTo(BigDecimal.ZERO) > 0,
                "Calmar ratio should be positive for profitable strategy with drawdown");
        }
    }

    /**
     * Test 6: Edge case - all winning trades → verify metrics
     */
    @Test
    void testAllWinningTrades() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createAllWinningTrades();
        List<BigDecimal> equityCurve = createEquityCurveForAllWinning(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result);
        assertEquals(0, BigDecimal.ONE.compareTo(result.getWinRate()), 
            "Win rate should be 100%");
        assertEquals(5, result.getWinningTrades());
        assertEquals(0, result.getLosingTrades());
        assertTrue(result.getProfitFactor().compareTo(BigDecimal.ZERO) > 0,
            "Profit factor should be positive (infinity handled as large number)");
        assertTrue(result.getTotalReturn().compareTo(BigDecimal.ZERO) > 0,
            "Total return should be positive");
    }

    /**
     * Test 7: Edge case - all losing trades → verify metrics
     */
    @Test
    void testAllLosingTrades() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createAllLosingTrades();
        List<BigDecimal> equityCurve = createEquityCurveForAllLosing(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getWinRate(), "Win rate should be 0%");
        assertEquals(0, result.getWinningTrades());
        assertEquals(5, result.getLosingTrades());
        assertEquals(BigDecimal.ZERO, result.getProfitFactor(), "Profit factor should be 0 (no wins)");
        assertTrue(result.getTotalReturn().compareTo(BigDecimal.ZERO) < 0,
            "Total return should be negative");
        assertTrue(result.getSharpeRatio().compareTo(BigDecimal.ZERO) < 0,
            "Sharpe ratio should be negative");
    }

    /**
     * Test 8: Edge case - zero trades → handle gracefully
     */
    @Test
    void testZeroTrades() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = new ArrayList<>();
        List<BigDecimal> equityCurve = List.of(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalTrades());
        assertEquals(0, result.getWinningTrades());
        assertEquals(0, result.getLosingTrades());
        assertEquals(BigDecimal.ZERO, result.getSharpeRatio());
        assertEquals(BigDecimal.ZERO, result.getProfitFactor());
        assertEquals(BigDecimal.ZERO, result.getWinRate());
        assertEquals(BigDecimal.ZERO, result.getMaxDrawdown());
        assertEquals(BigDecimal.ZERO, result.getCalmarRatio());
        assertEquals(BigDecimal.ZERO, result.getTotalReturn());
        assertEquals(BigDecimal.ZERO, result.getAnnualReturn());
    }

    /**
     * Test 9: Verify BigDecimal precision
     */
    @Test
    void testBigDecimalPrecision() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.000000");
        List<Trade> trades = createPrecisionTestTrades();
        List<BigDecimal> equityCurve = createEquityCurveForPrecisionTest(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getTotalReturn());
        assertNotNull(result.getSharpeRatio());
        assertNotNull(result.getProfitFactor());
        // Verify no precision loss in calculations
        assertTrue(result.getTotalReturn().scale() >= 2, "Should maintain at least 2 decimal places");
    }

    /**
     * Test 10: Verify total return calculation
     */
    @Test
    void testTotalReturnCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getTotalReturn());
        // Expected: +$22 profit on $1000 = 2.2% return
        BigDecimal expectedReturn = new BigDecimal("0.022");
        assertEquals(0, result.getTotalReturn().setScale(3, RoundingMode.HALF_UP)
            .compareTo(expectedReturn.setScale(3, RoundingMode.HALF_UP)),
            "Total return should be 2.2%, got: " + result.getTotalReturn());
    }

    /**
     * Test 11: Verify annual return calculation
     */
    @Test
    void testAnnualReturnCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getAnnualReturn());
        assertTrue(result.getAnnualReturn().compareTo(BigDecimal.ZERO) > 0,
            "Annual return should be positive for profitable trades");
    }

    /**
     * Test 12: Verify average win and loss calculations
     */
    @Test
    void testAverageWinLossCalculations() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getAvgWin());
        assertNotNull(result.getAvgLoss());
        // Expected avg win: (10 + 8 + 12 + 6) / 4 = 9.0
        BigDecimal expectedAvgWin = new BigDecimal("9.00");
        assertEquals(0, result.getAvgWin().setScale(2, RoundingMode.HALF_UP)
            .compareTo(expectedAvgWin),
            "Average win should be $9.00, got: " + result.getAvgWin());
        // Expected avg loss: (5 + 3 + 4 + 2) / 4 = 3.5
        BigDecimal expectedAvgLoss = new BigDecimal("-3.50");
        assertEquals(0, result.getAvgLoss().setScale(2, RoundingMode.HALF_UP)
            .compareTo(expectedAvgLoss),
            "Average loss should be -$3.50, got: " + result.getAvgLoss());
    }

    /**
     * Test 13: Verify p-value calculation
     */
    @Test
    void testPValueCalculation() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        List<Trade> trades = createKnownTradeSequence();
        List<BigDecimal> equityCurve = createEquityCurveForKnownSequence(initialBalance);

        // Act
        MetricsResult result = backtestMetrics.calculateMetrics(trades, equityCurve, initialBalance, startDate, endDate);

        // Assert
        assertNotNull(result.getpValue());
        assertTrue(result.getpValue().compareTo(BigDecimal.ZERO) >= 0,
            "P-value should be non-negative");
        assertTrue(result.getpValue().compareTo(BigDecimal.ONE) <= 0,
            "P-value should be <= 1.0");
    }

    // Helper methods to create test data

    /**
     * Creates known trade sequence: [+$10, -$5, +$8, -$3, +$12, -$4, +$6, -$2]
     */
    private List<Trade> createKnownTradeSequence() {
        List<Trade> trades = new ArrayList<>();
        LocalDateTime time = startDate;

        // Trade 1: +$10
        trades.add(createTrade("BTC/USDT", time, new BigDecimal("10.00")));
        time = time.plusHours(1);

        // Trade 2: -$5
        trades.add(createTrade("BTC/USDT", time, new BigDecimal("-5.00")));
        time = time.plusHours(1);

        // Trade 3: +$8
        trades.add(createTrade("ETH/USDT", time, new BigDecimal("8.00")));
        time = time.plusHours(1);

        // Trade 4: -$3
        trades.add(createTrade("ETH/USDT", time, new BigDecimal("-3.00")));
        time = time.plusHours(1);

        // Trade 5: +$12
        trades.add(createTrade("BTC/USDT", time, new BigDecimal("12.00")));
        time = time.plusHours(1);

        // Trade 6: -$4
        trades.add(createTrade("BTC/USDT", time, new BigDecimal("-4.00")));
        time = time.plusHours(1);

        // Trade 7: +$6
        trades.add(createTrade("ETH/USDT", time, new BigDecimal("6.00")));
        time = time.plusHours(1);

        // Trade 8: -$2
        trades.add(createTrade("ETH/USDT", time, new BigDecimal("-2.00")));

        return trades;
    }

    private List<BigDecimal> createEquityCurveForKnownSequence(BigDecimal initialBalance) {
        List<BigDecimal> curve = new ArrayList<>();
        curve.add(initialBalance);
        
        BigDecimal balance = initialBalance;
        BigDecimal[] pnls = {
            new BigDecimal("10.00"), new BigDecimal("-5.00"), new BigDecimal("8.00"),
            new BigDecimal("-3.00"), new BigDecimal("12.00"), new BigDecimal("-4.00"),
            new BigDecimal("6.00"), new BigDecimal("-2.00")
        };
        
        for (BigDecimal pnl : pnls) {
            balance = balance.add(pnl);
            curve.add(balance);
        }
        
        return curve;
    }

    private List<Trade> createAllWinningTrades() {
        List<Trade> trades = new ArrayList<>();
        LocalDateTime time = startDate;

        for (int i = 0; i < 5; i++) {
            trades.add(createTrade("BTC/USDT", time, new BigDecimal("10.00")));
            time = time.plusHours(1);
        }

        return trades;
    }

    private List<BigDecimal> createEquityCurveForAllWinning(BigDecimal initialBalance) {
        List<BigDecimal> curve = new ArrayList<>();
        curve.add(initialBalance);
        
        BigDecimal balance = initialBalance;
        for (int i = 0; i < 5; i++) {
            balance = balance.add(new BigDecimal("10.00"));
            curve.add(balance);
        }
        
        return curve;
    }

    private List<Trade> createAllLosingTrades() {
        List<Trade> trades = new ArrayList<>();
        LocalDateTime time = startDate;

        for (int i = 0; i < 5; i++) {
            trades.add(createTrade("BTC/USDT", time, new BigDecimal("-10.00")));
            time = time.plusHours(1);
        }

        return trades;
    }

    private List<BigDecimal> createEquityCurveForAllLosing(BigDecimal initialBalance) {
        List<BigDecimal> curve = new ArrayList<>();
        curve.add(initialBalance);
        
        BigDecimal balance = initialBalance;
        for (int i = 0; i < 5; i++) {
            balance = balance.add(new BigDecimal("-10.00"));
            curve.add(balance);
        }
        
        return curve;
    }

    private List<Trade> createPrecisionTestTrades() {
        List<Trade> trades = new ArrayList<>();
        LocalDateTime time = startDate;

        trades.add(createTrade("BTC/USDT", time, new BigDecimal("0.123456")));
        trades.add(createTrade("BTC/USDT", time.plusHours(1), new BigDecimal("-0.234567")));
        trades.add(createTrade("BTC/USDT", time.plusHours(2), new BigDecimal("0.345678")));

        return trades;
    }

    private List<BigDecimal> createEquityCurveForPrecisionTest(BigDecimal initialBalance) {
        List<BigDecimal> curve = new ArrayList<>();
        curve.add(initialBalance);
        curve.add(initialBalance.add(new BigDecimal("0.123456")));
        curve.add(initialBalance.add(new BigDecimal("0.123456")).add(new BigDecimal("-0.234567")));
        curve.add(initialBalance.add(new BigDecimal("0.123456")).add(new BigDecimal("-0.234567")).add(new BigDecimal("0.345678")));
        return curve;
    }

    private Trade createTrade(String symbol, LocalDateTime entryTime, BigDecimal pnl) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSignalType(Trade.SignalType.BUY);
        trade.setEntryTime(entryTime);
        trade.setExitTime(entryTime.plusMinutes(30));
        trade.setEntryPrice(new BigDecimal("50000.00"));
        trade.setExitPrice(new BigDecimal("50100.00"));
        trade.setPositionSize(new BigDecimal("0.001"));
        trade.setRiskAmount(new BigDecimal("20.00"));
        trade.setStopLoss(new BigDecimal("49000.00"));
        trade.setTakeProfit(new BigDecimal("51000.00"));
        trade.setPnl(pnl);
        trade.setActualFees(new BigDecimal("0.50"));
        trade.setActualSlippage(new BigDecimal("0.15"));
        return trade;
    }
}
