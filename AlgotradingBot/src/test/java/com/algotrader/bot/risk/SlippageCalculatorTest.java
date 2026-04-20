package com.algotrader.bot.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SlippageCalculator.
 * Verifies accurate calculation of transaction costs including fees and slippage.
 */
class SlippageCalculatorTest {

    private SlippageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SlippageCalculator();
    }

    @Test
    void testBuyAt100_EffectiveCost100_13() {
        // Given: Entry at $100 with quantity 1
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal quantity = BigDecimal.ONE;

        // When: Calculate buy cost
        TransactionCost result = calculator.calculateRealCost(price, quantity, true);

        // Then: Effective cost should be $100.13
        assertEquals(new BigDecimal("100.13000000"), result.getEffectivePrice());
        assertEquals(new BigDecimal("100.13000000"), result.getNetCost());
    }

    @Test
    void testSellAt105_EffectiveRevenue104_86() {
        // Given: Exit at $105 with quantity 1
        BigDecimal price = new BigDecimal("105.00");
        BigDecimal quantity = BigDecimal.ONE;

        // When: Calculate sell cost
        TransactionCost result = calculator.calculateRealCost(price, quantity, false);

        // Then: Effective revenue should be $104.86
        assertEquals(new BigDecimal("104.86350000"), result.getEffectivePrice());
        assertEquals(new BigDecimal("104.86350000"), result.getNetCost());
    }

    @Test
    void testFeesAndSlippageTrackedSeparately() {
        // Given: Buy at $100 with quantity 1
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal quantity = BigDecimal.ONE;

        // When: Calculate buy cost
        TransactionCost result = calculator.calculateRealCost(price, quantity, true);

        // Then: Fees and slippage should be tracked separately
        // Fees: 100 * 0.001 = 0.10
        assertEquals(new BigDecimal("0.10000000"), result.getTotalFees());
        
        // Slippage: 100 * 0.0003 = 0.03
        assertEquals(new BigDecimal("0.03000000"), result.getTotalSlippage());
    }

    @Test
    void testRoundTripTradeCost() {
        // Given: Buy at $100, sell at $105
        BigDecimal buyPrice = new BigDecimal("100.00");
        BigDecimal sellPrice = new BigDecimal("105.00");
        BigDecimal quantity = BigDecimal.ONE;

        // When: Calculate both costs
        TransactionCost buyCost = calculator.calculateRealCost(buyPrice, quantity, true);
        TransactionCost sellCost = calculator.calculateRealCost(sellPrice, quantity, false);

        // Then: Verify total cost impact
        // Buy cost: $100.13
        // Sell revenue: $104.86
        // Net profit: $104.86 - $100.13 = $4.73 (vs $5.00 without costs)
        BigDecimal netProfit = sellCost.getNetCost().subtract(buyCost.getNetCost());
        assertEquals(new BigDecimal("4.73350000"), netProfit);
    }

    @Test
    void testLargePositionSlippageScales() {
        // Given: Large position - 10 units at $1000
        BigDecimal price = new BigDecimal("1000.00");
        BigDecimal quantity = new BigDecimal("10.00");

        // When: Calculate buy cost
        TransactionCost result = calculator.calculateRealCost(price, quantity, true);

        // Then: Fees and slippage should scale proportionally
        // Notional: 1000 * 10 = 10,000
        // Fees: 10,000 * 0.001 = 10.00
        // Slippage: 10,000 * 0.0003 = 3.00
        assertEquals(new BigDecimal("10.00000000"), result.getTotalFees());
        assertEquals(new BigDecimal("3.00000000"), result.getTotalSlippage());
        assertEquals(new BigDecimal("10013.00000000"), result.getNetCost());
    }

    @Test
    void testBigDecimalPrecision() {
        // Given: Price with many decimal places
        BigDecimal price = new BigDecimal("45123.456789");
        BigDecimal quantity = new BigDecimal("0.123456");

        // When: Calculate buy cost
        TransactionCost result = calculator.calculateRealCost(price, quantity, true);

        // Then: Should maintain precision without rounding errors
        assertNotNull(result.getEffectivePrice());
        assertNotNull(result.getTotalFees());
        assertNotNull(result.getTotalSlippage());
        assertNotNull(result.getNetCost());
        
        // Verify calculations are consistent
        BigDecimal notional = price.multiply(quantity);
        BigDecimal expectedFees = notional.multiply(new BigDecimal("0.001"));
        BigDecimal expectedSlippage = notional.multiply(new BigDecimal("0.0003"));
        
        // Allow for rounding differences in the 8th decimal place
        assertTrue(result.getTotalFees().subtract(expectedFees).abs()
                .compareTo(new BigDecimal("0.00000001")) < 0);
        assertTrue(result.getTotalSlippage().subtract(expectedSlippage).abs()
                .compareTo(new BigDecimal("0.00000001")) < 0);
    }

    @Test
    void testInvalidPrice_ThrowsException() {
        // Given: Invalid price (zero)
        BigDecimal price = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ONE;

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateRealCost(price, quantity, true);
        });
    }

    @Test
    void testInvalidQuantity_ThrowsException() {
        // Given: Invalid quantity (negative)
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal quantity = new BigDecimal("-1.00");

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateRealCost(price, quantity, true);
        });
    }

    @Test
    void testNullPrice_ThrowsException() {
        // Given: Null price
        BigDecimal quantity = BigDecimal.ONE;

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateRealCost(null, quantity, true);
        });
    }

    @Test
    void testNullQuantity_ThrowsException() {
        // Given: Null quantity
        BigDecimal price = new BigDecimal("100.00");

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateRealCost(price, null, true);
        });
    }
}
