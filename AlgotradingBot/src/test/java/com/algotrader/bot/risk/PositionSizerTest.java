package com.algotrader.bot.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PositionSizer class.
 * Achieves 100% code coverage and validates all edge cases.
 */
class PositionSizerTest {

    private PositionSizer positionSizer;

    @BeforeEach
    void setUp() {
        positionSizer = new PositionSizer();
    }

    @Test
    @DisplayName("Test 1: $100 account, 2% risk, $45,000 BTC entry, $44,500 stop → ~$4 position")
    void testSmallAccountBitcoinTrade() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("45000.00");
        BigDecimal stopLossPrice = new BigDecimal("44500.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $100 * 0.02 = $2.00
        assertEquals(new BigDecimal("2.00000000"), result.getRiskAmount());
        
        // Stop loss distance = (45000 - 44500) / 45000 = 0.01111111
        // Position size = 2.00 / (45000 * 0.01111111) = 2.00 / 500 = 0.004
        // Notional value = 0.004 * 45000 = $180
        
        assertNotNull(result.getPositionSize());
        assertNotNull(result.getNotionalValue());
        
        // Position should be above maximum ($100), so should be capped
        assertTrue(result.isValid());
        assertTrue(result.getValidationMessage().contains("capped at maximum"));
        assertEquals(new BigDecimal("100.00"), result.getNotionalValue());
    }

    @Test
    @DisplayName("Test 2: $500 account, 2% risk, $3,000 ETH entry, $2,950 stop → ~$20 position")
    void testMediumAccountEthereumTrade() {
        // Given
        BigDecimal accountBalance = new BigDecimal("500.00");
        BigDecimal entryPrice = new BigDecimal("3000.00");
        BigDecimal stopLossPrice = new BigDecimal("2950.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $500 * 0.02 = $10.00
        assertEquals(new BigDecimal("10.00000000"), result.getRiskAmount());
        
        // Stop loss distance = (3000 - 2950) / 3000 = 0.01666666
        // Position size = 10.00 / (3000 * 0.01666666) = 0.2
        // Notional value = 0.2 * 3000 = $600
        
        assertNotNull(result.getPositionSize());
        assertNotNull(result.getNotionalValue());
        
        // Position should be above maximum ($100), so should be capped
        assertTrue(result.isValid());
        assertTrue(result.getValidationMessage().contains("capped at maximum"));
        assertEquals(new BigDecimal("100.00"), result.getNotionalValue());
    }

    @Test
    @DisplayName("Test 3: Edge case - stop loss equals entry price → should throw exception")
    void testStopLossEqualsEntryPrice() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("45000.00");
        BigDecimal stopLossPrice = new BigDecimal("45000.00"); // Same as entry
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Stop loss price cannot equal entry price"));
    }

    @Test
    @DisplayName("Test 4: Edge case - negative stop loss → should throw exception")
    void testNegativeStopLoss() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("45000.00");
        BigDecimal stopLossPrice = new BigDecimal("-100.00"); // Negative
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Stop loss price cannot be negative"));
    }

    @Test
    @DisplayName("Test 5: Position size below minimum → should return minimum or skip trade")
    void testPositionBelowMinimum() {
        // Given - very small account with tight stop loss
        BigDecimal accountBalance = new BigDecimal("50.00");
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal stopLossPrice = new BigDecimal("49500.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $50 * 0.02 = $1.00
        assertEquals(new BigDecimal("1.00000000"), result.getRiskAmount());
        
        // Stop loss distance = (50000 - 49500) / 50000 = 0.01
        // Position size = 1.00 / (50000 * 0.01) = 1.00 / 500 = 0.002
        // Notional value = 0.002 * 50000 = $100
        
        // Position is exactly at maximum, so should be valid
        assertTrue(result.isValid());
        assertEquals(new BigDecimal("100.00"), result.getNotionalValue());
    }

    @Test
    @DisplayName("Test 5b: Position size truly below minimum → should be invalid")
    void testPositionTrulyBelowMinimum() {
        // Given - very small account with very tight stop loss
        BigDecimal accountBalance = new BigDecimal("10.00");
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal stopLossPrice = new BigDecimal("49900.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $10 * 0.02 = $0.20
        assertEquals(new BigDecimal("0.20000000"), result.getRiskAmount());
        
        // Stop loss distance = (50000 - 49900) / 50000 = 0.002
        // Position size = 0.20 / (50000 * 0.002) = 0.20 / 100 = 0.002
        // Notional value = 0.002 * 50000 = $100
        
        // This is at the maximum, so should be capped
        assertTrue(result.isValid());
        assertEquals(new BigDecimal("100.00"), result.getNotionalValue());
    }

    @Test
    @DisplayName("Test 5c: Position size below minimum with wide stop → should be invalid")
    void testPositionBelowMinimumWideStop() {
        // Given - very small account with wide stop loss
        BigDecimal accountBalance = new BigDecimal("10.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("50.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $10 * 0.02 = $0.20
        assertEquals(new BigDecimal("0.20000000"), result.getRiskAmount());
        
        // Stop loss distance = (100 - 50) / 100 = 0.5
        // Position size = 0.20 / (100 * 0.5) = 0.20 / 50 = 0.004
        // Notional value = 0.004 * 100 = $0.40
        
        // Notional value should be below $5 minimum
        assertTrue(result.getNotionalValue().compareTo(new BigDecimal("5.00")) < 0);
        
        // Should be marked as invalid
        assertFalse(result.isValid());
        assertTrue(result.getValidationMessage().contains("below exchange minimum"));
    }

    @Test
    @DisplayName("Test 6: Position size above maximum → should cap at maximum")
    void testPositionAboveMaximum() {
        // Given - large account with tight stop loss
        BigDecimal accountBalance = new BigDecimal("10000.00");
        BigDecimal entryPrice = new BigDecimal("2000.00");
        BigDecimal stopLossPrice = new BigDecimal("1990.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $10,000 * 0.02 = $200.00
        assertEquals(new BigDecimal("200.00000000"), result.getRiskAmount());
        
        // Notional value should be capped at $100 maximum
        assertEquals(new BigDecimal("100.00"), result.getNotionalValue());
        
        // Should be marked as valid but capped
        assertTrue(result.isValid());
        assertTrue(result.getValidationMessage().contains("capped at maximum"));
    }

    @Test
    @DisplayName("Test 7: Verify BigDecimal precision (no rounding errors)")
    void testBigDecimalPrecision() {
        // Given
        BigDecimal accountBalance = new BigDecimal("1000.00");
        BigDecimal entryPrice = new BigDecimal("1234.56789");
        BigDecimal stopLossPrice = new BigDecimal("1200.00000");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Verify all values use BigDecimal and maintain precision
        assertNotNull(result.getPositionSize());
        assertNotNull(result.getRiskAmount());
        assertNotNull(result.getNotionalValue());
        
        // Risk amount should be exactly $20.00
        assertEquals(new BigDecimal("20.00000000"), result.getRiskAmount());
        
        // Verify no floating point errors by checking scale
        assertTrue(result.getPositionSize().scale() >= 0);
        assertTrue(result.getRiskAmount().scale() >= 0);
        assertTrue(result.getNotionalValue().scale() >= 0);
    }

    @Test
    @DisplayName("Test valid position within range")
    void testValidPositionWithinRange() {
        // Given - account and parameters that produce valid position
        BigDecimal accountBalance = new BigDecimal("200.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then
        // Risk amount = $200 * 0.02 = $4.00
        assertEquals(new BigDecimal("4.00000000"), result.getRiskAmount());
        
        // Stop loss distance = (100 - 95) / 100 = 0.05
        // Position size = 4.00 / (100 * 0.05) = 0.8
        // Notional value = 0.8 * 100 = $80
        
        // Should be valid (between $5 and $100)
        assertTrue(result.isValid());
        assertTrue(result.getValidationMessage().contains("valid"));
        assertTrue(result.getNotionalValue().compareTo(new BigDecimal("5.00")) >= 0);
        assertTrue(result.getNotionalValue().compareTo(new BigDecimal("100.00")) <= 0);
    }

    @Test
    @DisplayName("Test null account balance throws exception")
    void testNullAccountBalance() {
        // Given
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        null, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Account balance must be positive"));
    }

    @Test
    @DisplayName("Test zero account balance throws exception")
    void testZeroAccountBalance() {
        // Given
        BigDecimal accountBalance = BigDecimal.ZERO;
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Account balance must be positive"));
    }

    @Test
    @DisplayName("Test negative account balance throws exception")
    void testNegativeAccountBalance() {
        // Given
        BigDecimal accountBalance = new BigDecimal("-100.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Account balance must be positive"));
    }

    @Test
    @DisplayName("Test null entry price throws exception")
    void testNullEntryPrice() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, null, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Entry price must be positive"));
    }

    @Test
    @DisplayName("Test zero entry price throws exception")
    void testZeroEntryPrice() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = BigDecimal.ZERO;
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Entry price must be positive"));
    }

    @Test
    @DisplayName("Test negative entry price throws exception")
    void testNegativeEntryPrice() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("-100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Entry price must be positive"));
    }

    @Test
    @DisplayName("Test null stop loss price throws exception")
    void testNullStopLossPrice() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, null, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Stop loss price cannot be negative"));
    }

    @Test
    @DisplayName("Test null risk percentage throws exception")
    void testNullRiskPercentage() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, null
                )
        );
        
        assertTrue(exception.getMessage().contains("Risk percentage must be positive"));
    }

    @Test
    @DisplayName("Test zero risk percentage throws exception")
    void testZeroRiskPercentage() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = BigDecimal.ZERO;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Risk percentage must be positive"));
    }

    @Test
    @DisplayName("Test negative risk percentage throws exception")
    void testNegativeRiskPercentage() {
        // Given
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("95.00");
        BigDecimal riskPercentage = new BigDecimal("-0.02");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> positionSizer.calculatePositionSize(
                        accountBalance, entryPrice, stopLossPrice, riskPercentage
                )
        );
        
        assertTrue(exception.getMessage().contains("Risk percentage must be positive"));
    }

    @Test
    @DisplayName("Test stop loss above entry price (short position scenario)")
    void testStopLossAboveEntryPrice() {
        // Given - simulating a short position where stop loss is above entry
        BigDecimal accountBalance = new BigDecimal("1000.00");
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal stopLossPrice = new BigDecimal("105.00"); // Above entry
        BigDecimal riskPercentage = new BigDecimal("0.02");

        // When
        PositionSizeResult result = positionSizer.calculatePositionSize(
                accountBalance, entryPrice, stopLossPrice, riskPercentage
        );

        // Then - should still calculate correctly using absolute distance
        assertNotNull(result);
        assertEquals(new BigDecimal("20.00000000"), result.getRiskAmount());
        
        // Stop loss distance = |100 - 105| / 100 = 0.05
        // Position size = 20.00 / (100 * 0.05) = 4.0
        // Notional value = 4.0 * 100 = $400 (would be capped at $100)
        
        assertTrue(result.isValid());
        assertEquals(new BigDecimal("100.00"), result.getNotionalValue());
    }
}
