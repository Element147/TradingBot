package com.algotrader.bot.backtest;

import com.algotrader.bot.entity.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MonteCarloSimulator.
 * Tests randomization, statistics calculation, and edge cases.
 */
class MonteCarloSimulatorTest {

    private MonteCarloSimulator simulator;
    private BigDecimal initialBalance;

    @BeforeEach
    void setUp() {
        simulator = new MonteCarloSimulator();
        initialBalance = new BigDecimal("10000.00");
    }

    @Test
    void testSimulateWithKnownTradeSequence_VerifyRandomization() {
        // Given: Known trade sequence with varying PnL
        List<Trade> trades = createTradeSequence(
                new BigDecimal("100"),
                new BigDecimal("-50"),
                new BigDecimal("80"),
                new BigDecimal("-30")
        );

        // When: Run simulation with seed for reproducibility
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100, 12345L);

        // Then: Verify simulation ran successfully
        assertEquals(100, result.getTotalIterations());
        assertTrue(result.getProfitableIterations() > 0);
        assertTrue(result.getAverageFinalBalance().compareTo(initialBalance) > 0);
        
        // Verify percentiles are ordered correctly
        assertTrue(result.getPercentile5th().compareTo(result.getPercentile50th()) <= 0);
        assertTrue(result.getPercentile50th().compareTo(result.getPercentile95th()) <= 0);
    }

    @Test
    void testSimulateWithAllWinningTrades_100PercentProfitable() {
        // Given: All winning trades
        List<Trade> trades = createTradeSequence(
                new BigDecimal("100"),
                new BigDecimal("80"),
                new BigDecimal("120"),
                new BigDecimal("90")
        );

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: 100% of iterations should be profitable
        assertEquals(100, result.getProfitableIterations());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getProfitablePercentage()));
        assertTrue(result.getAverageFinalBalance().compareTo(initialBalance) > 0);
        
        // All percentiles should be above initial balance
        assertTrue(result.getPercentile5th().compareTo(initialBalance) > 0);
        assertTrue(result.getPercentile50th().compareTo(initialBalance) > 0);
        assertTrue(result.getPercentile95th().compareTo(initialBalance) > 0);
    }

    @Test
    void testSimulateWithMixedTrades_VerifyConfidenceIntervals() {
        // Given: Mixed winning and losing trades
        List<Trade> trades = createTradeSequence(
                new BigDecimal("150"),
                new BigDecimal("-80"),
                new BigDecimal("120"),
                new BigDecimal("-40"),
                new BigDecimal("100"),
                new BigDecimal("-60")
        );

        // When: Run simulation with 1000 iterations
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 1000);

        // Then: Verify confidence intervals
        assertEquals(1000, result.getTotalIterations());
        assertTrue(result.getProfitableIterations() > 0);
        
        // Verify percentiles are properly ordered
        assertTrue(result.getPercentile5th().compareTo(result.getPercentile50th()) <= 0);
        assertTrue(result.getPercentile50th().compareTo(result.getPercentile95th()) <= 0);
        
        // Verify average is reasonable
        BigDecimal expectedTotal = new BigDecimal("190"); // Sum of all PnL
        BigDecimal expectedFinal = initialBalance.add(expectedTotal);
        assertTrue(result.getAverageFinalBalance().subtract(expectedFinal).abs()
                .compareTo(new BigDecimal("1")) < 0); // Within $1
    }

    @Test
    void testSimulateVerifyStatistics_MeanMedianPercentiles() {
        // Given: Trade sequence with known total PnL
        List<Trade> trades = createTradeSequence(
                new BigDecimal("50"),
                new BigDecimal("50"),
                new BigDecimal("50")
        );

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: All iterations should have same final balance (no variance in PnL)
        BigDecimal expectedFinal = initialBalance.add(new BigDecimal("150"));
        
        // Average should equal expected
        assertTrue(result.getAverageFinalBalance().subtract(expectedFinal).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        
        // All percentiles should be very close to expected
        assertTrue(result.getPercentile5th().subtract(expectedFinal).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        assertTrue(result.getPercentile50th().subtract(expectedFinal).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        assertTrue(result.getPercentile95th().subtract(expectedFinal).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    void testSimulateVerifyReproducibilityWithSeed() {
        // Given: Trade sequence
        List<Trade> trades = createTradeSequence(
                new BigDecimal("100"),
                new BigDecimal("-50"),
                new BigDecimal("80")
        );

        // When: Run simulation twice with same seed
        MonteCarloResult result1 = simulator.simulate(trades, initialBalance, 100, 42L);
        MonteCarloResult result2 = simulator.simulate(trades, initialBalance, 100, 42L);

        // Then: Results should be identical
        assertEquals(result1.getProfitableIterations(), result2.getProfitableIterations());
        assertEquals(0, result1.getAverageFinalBalance().compareTo(result2.getAverageFinalBalance()));
        assertEquals(0, result1.getWorstCaseDrawdown().compareTo(result2.getWorstCaseDrawdown()));
        assertEquals(0, result1.getPercentile5th().compareTo(result2.getPercentile5th()));
        assertEquals(0, result1.getPercentile50th().compareTo(result2.getPercentile50th()));
        assertEquals(0, result1.getPercentile95th().compareTo(result2.getPercentile95th()));
    }

    @Test
    void testSimulatePerformance_1000IterationsUnder10Seconds() {
        // Given: Realistic trade sequence (30 trades)
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            BigDecimal pnl = i % 2 == 0 ? new BigDecimal("50") : new BigDecimal("-30");
            trades.add(createTrade("BTC/USDT", pnl));
        }

        // When: Run 1000 iterations and measure time
        long startTime = System.currentTimeMillis();
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 1000);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should complete in under 10 seconds
        assertTrue(duration < 10000, "Simulation took " + duration + "ms, expected < 10000ms");
        assertEquals(1000, result.getTotalIterations());
    }

    @Test
    void testSimulateWithEmptyTradeList_ReturnsZeroResult() {
        // Given: Empty trade list
        List<Trade> trades = new ArrayList<>();

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: Should return zero result
        assertEquals(100, result.getTotalIterations());
        assertEquals(0, result.getProfitableIterations());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getProfitablePercentage()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAverageFinalBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getWorstCaseDrawdown()));
    }

    @Test
    void testSimulateWithNullTradeList_ReturnsZeroResult() {
        // Given: Null trade list
        List<Trade> trades = null;

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: Should return zero result without throwing exception
        assertEquals(100, result.getTotalIterations());
        assertEquals(0, result.getProfitableIterations());
    }

    @Test
    void testSimulateWithNullInitialBalance_ThrowsException() {
        // Given: Valid trades but null initial balance
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            simulator.simulate(trades, null, 100);
        });
    }

    @Test
    void testSimulateWithZeroInitialBalance_ThrowsException() {
        // Given: Valid trades but zero initial balance
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            simulator.simulate(trades, BigDecimal.ZERO, 100);
        });
    }

    @Test
    void testSimulateWithNegativeInitialBalance_ThrowsException() {
        // Given: Valid trades but negative initial balance
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            simulator.simulate(trades, new BigDecimal("-1000"), 100);
        });
    }

    @Test
    void testSimulateWithZeroIterations_ThrowsException() {
        // Given: Valid trades but zero iterations
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            simulator.simulate(trades, initialBalance, 0);
        });
    }

    @Test
    void testSimulateWithNegativeIterations_ThrowsException() {
        // Given: Valid trades but negative iterations
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            simulator.simulate(trades, initialBalance, -10);
        });
    }

    @Test
    void testSimulateDefaultIterations_Uses1000() {
        // Given: Trade sequence
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When: Run simulation without specifying iterations
        MonteCarloResult result = simulator.simulate(trades, initialBalance);

        // Then: Should use default 1000 iterations
        assertEquals(1000, result.getTotalIterations());
    }

    @Test
    void testSimulateWithTradesHavingNullPnL_HandlesGracefully() {
        // Given: Trades with some null PnL values
        List<Trade> trades = new ArrayList<>();
        trades.add(createTrade("BTC/USDT", new BigDecimal("100")));
        trades.add(createTrade("BTC/USDT", null)); // Null PnL
        trades.add(createTrade("BTC/USDT", new BigDecimal("50")));

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: Should handle gracefully and calculate based on non-null PnL
        assertEquals(100, result.getTotalIterations());
        assertTrue(result.getAverageFinalBalance().compareTo(initialBalance) > 0);
    }

    @Test
    void testSimulateVerifyWorstCaseDrawdown() {
        // Given: Trades with significant drawdown potential
        List<Trade> trades = createTradeSequence(
                new BigDecimal("500"),   // Win
                new BigDecimal("-800"),  // Large loss
                new BigDecimal("200"),   // Small win
                new BigDecimal("-300")   // Loss
        );

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: Worst case drawdown should be captured
        assertTrue(result.getWorstCaseDrawdown().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.getWorstCaseDrawdown().compareTo(new BigDecimal("1.0")) < 0); // Less than 100%
    }

    @Test
    void testSimulateVerifyAverageSharpeRatio() {
        // Given: Trade sequence with positive returns
        List<Trade> trades = createTradeSequence(
                new BigDecimal("100"),
                new BigDecimal("80"),
                new BigDecimal("120")
        );

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: Average Sharpe ratio should be calculated
        assertNotNull(result.getAverageSharpeRatio());
        assertTrue(result.getAverageSharpeRatio().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void testSimulateWithAllLosingTrades_ZeroProfitableIterations() {
        // Given: All losing trades
        List<Trade> trades = createTradeSequence(
                new BigDecimal("-50"),
                new BigDecimal("-30"),
                new BigDecimal("-40")
        );

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 100);

        // Then: Zero profitable iterations
        assertEquals(0, result.getProfitableIterations());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getProfitablePercentage()));
        assertTrue(result.getAverageFinalBalance().compareTo(initialBalance) < 0);
    }

    @Test
    void testSimulateWithSingleTrade_HandlesCorrectly() {
        // Given: Single trade
        List<Trade> trades = createTradeSequence(new BigDecimal("100"));

        // When: Run simulation
        MonteCarloResult result = simulator.simulate(trades, initialBalance, 50);

        // Then: Should handle single trade correctly
        assertEquals(50, result.getTotalIterations());
        assertEquals(50, result.getProfitableIterations()); // All iterations profitable
        
        BigDecimal expectedFinal = initialBalance.add(new BigDecimal("100"));
        assertTrue(result.getAverageFinalBalance().subtract(expectedFinal).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
    }

    // Helper methods

    /**
     * Create a list of trades with specified PnL values.
     */
    private List<Trade> createTradeSequence(BigDecimal... pnlValues) {
        List<Trade> trades = new ArrayList<>();
        for (BigDecimal pnl : pnlValues) {
            trades.add(createTrade("BTC/USDT", pnl));
        }
        return trades;
    }

    /**
     * Create a single trade with specified symbol and PnL.
     */
    private Trade createTrade(String symbol, BigDecimal pnl) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSignalType(Trade.SignalType.BUY);
        trade.setEntryTime(LocalDateTime.now());
        trade.setEntryPrice(new BigDecimal("50000.00"));
        trade.setPositionSize(new BigDecimal("0.01"));
        trade.setRiskAmount(new BigDecimal("200.00"));
        trade.setStopLoss(new BigDecimal("48000.00"));
        trade.setTakeProfit(new BigDecimal("52000.00"));
        trade.setActualFees(new BigDecimal("5.00"));
        trade.setActualSlippage(new BigDecimal("1.50"));
        trade.setPnl(pnl);
        return trade;
    }
}
