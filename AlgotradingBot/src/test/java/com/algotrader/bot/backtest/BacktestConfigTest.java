package com.algotrader.bot.backtest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BacktestConfig.
 * Verifies builder pattern, validation logic, and immutability.
 */
class BacktestConfigTest {

    @Test
    void testBuilderWithAllParameters() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);
        Map<String, Object> params = new HashMap<>();
        params.put("bbPeriod", 20);
        params.put("bbStdDev", 2.0);

        BacktestConfig config = new BacktestConfig.Builder()
                .symbol("BTC/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .riskPerTrade(new BigDecimal("0.02"))
                .maxDrawdownLimit(new BigDecimal("0.25"))
                .commissionRate(new BigDecimal("0.001"))
                .slippageRate(new BigDecimal("0.0003"))
                .strategyParameters(params)
                .build();

        assertEquals("BTC/USDT", config.getSymbol());
        assertEquals(start, config.getStartDate());
        assertEquals(end, config.getEndDate());
        assertEquals(new BigDecimal("1000.00"), config.getInitialBalance());
        assertEquals(new BigDecimal("0.02"), config.getRiskPerTrade());
        assertEquals(new BigDecimal("0.25"), config.getMaxDrawdownLimit());
        assertEquals(new BigDecimal("0.001"), config.getCommissionRate());
        assertEquals(new BigDecimal("0.0003"), config.getSlippageRate());
        assertEquals(2, config.getStrategyParameters().size());
        assertEquals(20, config.getStrategyParameters().get("bbPeriod"));
    }

    @Test
    void testBuilderWithDefaults() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        BacktestConfig config = new BacktestConfig.Builder()
                .symbol("ETH/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("500.00"))
                .build();

        assertEquals("ETH/USDT", config.getSymbol());
        assertEquals(new BigDecimal("0.02"), config.getRiskPerTrade()); // Default 2%
        assertEquals(new BigDecimal("0.25"), config.getMaxDrawdownLimit()); // Default 25%
        assertEquals(new BigDecimal("0.001"), config.getCommissionRate()); // Default 0.1%
        assertEquals(new BigDecimal("0.0003"), config.getSlippageRate()); // Default 0.03%
        assertTrue(config.getStrategyParameters().isEmpty());
    }

    @Test
    void testAddStrategyParameterIndividually() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        BacktestConfig config = new BacktestConfig.Builder()
                .symbol("BTC/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .addStrategyParameter("bbPeriod", 20)
                .addStrategyParameter("bbStdDev", 2.0)
                .build();

        assertEquals(2, config.getStrategyParameters().size());
        assertEquals(20, config.getStrategyParameters().get("bbPeriod"));
        assertEquals(2.0, config.getStrategyParameters().get("bbStdDev"));
    }

    @Test
    void testValidationFailsWithNullSymbol() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol(null)
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .build();
        });

        assertEquals("Symbol cannot be null or empty", exception.getMessage());
    }

    @Test
    void testValidationFailsWithEmptySymbol() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("   ")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .build();
        });

        assertEquals("Symbol cannot be null or empty", exception.getMessage());
    }

    @Test
    void testValidationFailsWithNullStartDate() {
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(null)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .build();
        });

        assertEquals("Start date cannot be null", exception.getMessage());
    }

    @Test
    void testValidationFailsWithNullEndDate() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(null)
                    .initialBalance(new BigDecimal("1000.00"))
                    .build();
        });

        assertEquals("End date cannot be null", exception.getMessage());
    }

    @Test
    void testValidationFailsWhenStartDateAfterEndDate() {
        LocalDateTime start = LocalDateTime.of(2024, 2, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .build();
        });

        assertEquals("Start date must be before end date", exception.getMessage());
    }

    @Test
    void testValidationFailsWithNullInitialBalance() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(null)
                    .build();
        });

        assertEquals("Initial balance must be positive", exception.getMessage());
    }

    @Test
    void testValidationFailsWithZeroInitialBalance() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(BigDecimal.ZERO)
                    .build();
        });

        assertEquals("Initial balance must be positive", exception.getMessage());
    }

    @Test
    void testValidationFailsWithNegativeInitialBalance() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("-100.00"))
                    .build();
        });

        assertEquals("Initial balance must be positive", exception.getMessage());
    }

    @Test
    void testValidationFailsWithInvalidRiskPerTrade() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        // Risk = 0
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .riskPerTrade(BigDecimal.ZERO)
                    .build();
        });
        assertEquals("Risk per trade must be between 0 and 1", exception1.getMessage());

        // Risk = 1.0 (100%)
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .riskPerTrade(BigDecimal.ONE)
                    .build();
        });
        assertEquals("Risk per trade must be between 0 and 1", exception2.getMessage());

        // Risk > 1.0
        IllegalArgumentException exception3 = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .riskPerTrade(new BigDecimal("1.5"))
                    .build();
        });
        assertEquals("Risk per trade must be between 0 and 1", exception3.getMessage());
    }

    @Test
    void testValidationFailsWithInvalidMaxDrawdownLimit() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .maxDrawdownLimit(new BigDecimal("1.5"))
                    .build();
        });

        assertEquals("Max drawdown limit must be between 0 and 1", exception.getMessage());
    }

    @Test
    void testValidationFailsWithNegativeCommissionRate() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .commissionRate(new BigDecimal("-0.001"))
                    .build();
        });

        assertEquals("Commission rate cannot be negative", exception.getMessage());
    }

    @Test
    void testValidationFailsWithNegativeSlippageRate() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new BacktestConfig.Builder()
                    .symbol("BTC/USDT")
                    .startDate(start)
                    .endDate(end)
                    .initialBalance(new BigDecimal("1000.00"))
                    .slippageRate(new BigDecimal("-0.0003"))
                    .build();
        });

        assertEquals("Slippage rate cannot be negative", exception.getMessage());
    }

    @Test
    void testImmutabilityOfStrategyParameters() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);
        Map<String, Object> params = new HashMap<>();
        params.put("bbPeriod", 20);

        BacktestConfig config = new BacktestConfig.Builder()
                .symbol("BTC/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .strategyParameters(params)
                .build();

        // Modify original map
        params.put("bbPeriod", 30);
        params.put("newParam", "test");

        // Config should not be affected
        assertEquals(1, config.getStrategyParameters().size());
        assertEquals(20, config.getStrategyParameters().get("bbPeriod"));
        assertNull(config.getStrategyParameters().get("newParam"));

        // Modify returned map
        Map<String, Object> returnedParams = config.getStrategyParameters();
        returnedParams.put("anotherParam", "value");

        // Config should not be affected
        assertEquals(1, config.getStrategyParameters().size());
        assertNull(config.getStrategyParameters().get("anotherParam"));
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        BacktestConfig config1 = new BacktestConfig.Builder()
                .symbol("BTC/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        BacktestConfig config2 = new BacktestConfig.Builder()
                .symbol("BTC/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        BacktestConfig config3 = new BacktestConfig.Builder()
                .symbol("ETH/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
        assertNotEquals(config1.hashCode(), config3.hashCode());
    }

    @Test
    void testToString() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 2, 1, 0, 0);

        BacktestConfig config = new BacktestConfig.Builder()
                .symbol("BTC/USDT")
                .startDate(start)
                .endDate(end)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        String toString = config.toString();
        assertTrue(toString.contains("BTC/USDT"));
        assertTrue(toString.contains("1000.00"));
        assertTrue(toString.contains("0.02"));
    }
}
