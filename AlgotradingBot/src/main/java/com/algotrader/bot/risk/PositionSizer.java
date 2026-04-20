package com.algotrader.bot.risk;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Position sizing calculator that enforces the 2% risk rule.
 * Uses BigDecimal for all monetary calculations to ensure precision.
 */
@Component
public class PositionSizer {

    // Exchange minimum position size in USD
    private static final BigDecimal MIN_POSITION_SIZE = new BigDecimal("5.00");
    
    // Maximum position size per trade in USD
    private static final BigDecimal MAX_POSITION_SIZE = new BigDecimal("100.00");
    
    // Scale for BigDecimal calculations
    private static final int SCALE = 8;

    /**
     * Calculate position size based on account balance and risk parameters.
     * 
     * Formula: position_size = (account * risk%) / (entry_price * stop_loss_distance)
     * where stop_loss_distance = |entry_price - stop_loss_price| / entry_price
     * 
     * @param accountBalance Current account balance in USD
     * @param entryPrice Entry price for the asset
     * @param stopLossPrice Stop loss price for the asset
     * @param riskPercentage Risk percentage (e.g., 0.02 for 2%)
     * @return PositionSizeResult containing position size, risk amount, and notional value
     * @throws IllegalArgumentException if inputs are invalid
     */
    public PositionSizeResult calculatePositionSize(
            BigDecimal accountBalance,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            BigDecimal riskPercentage) {

        // Validate inputs
        validateInputs(accountBalance, entryPrice, stopLossPrice, riskPercentage);

        // Calculate risk amount in dollars
        BigDecimal riskAmount = accountBalance.multiply(riskPercentage)
                .setScale(SCALE, RoundingMode.DOWN);

        // Calculate stop loss distance as a percentage
        BigDecimal stopLossDistance = entryPrice.subtract(stopLossPrice).abs()
                .divide(entryPrice, SCALE, RoundingMode.DOWN);

        // Prevent division by zero
        if (stopLossDistance.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Stop loss distance cannot be zero (stop loss equals entry price)");
        }

        // Calculate position size in asset units
        // position_size = risk_amount / (entry_price * stop_loss_distance)
        BigDecimal positionSize = riskAmount.divide(
                entryPrice.multiply(stopLossDistance),
                SCALE,
                RoundingMode.DOWN
        );

        // Calculate notional value (position size * entry price)
        BigDecimal notionalValue = positionSize.multiply(entryPrice)
                .setScale(2, RoundingMode.DOWN);

        // Validate position size against exchange limits
        return validatePositionSize(positionSize, riskAmount, notionalValue);
    }

    /**
     * Validate input parameters.
     */
    private void validateInputs(BigDecimal accountBalance, BigDecimal entryPrice,
                                BigDecimal stopLossPrice, BigDecimal riskPercentage) {
        if (accountBalance == null || accountBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Account balance must be positive");
        }
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Entry price must be positive");
        }
        if (stopLossPrice == null || stopLossPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Stop loss price cannot be negative");
        }
        if (riskPercentage == null || riskPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Risk percentage must be positive");
        }
        if (stopLossPrice.compareTo(entryPrice) == 0) {
            throw new IllegalArgumentException("Stop loss price cannot equal entry price");
        }
    }

    /**
     * Validate position size against exchange and risk limits.
     */
    private PositionSizeResult validatePositionSize(BigDecimal positionSize,
                                                    BigDecimal riskAmount,
                                                    BigDecimal notionalValue) {
        // Check minimum position size
        if (notionalValue.compareTo(MIN_POSITION_SIZE) < 0) {
            return new PositionSizeResult(
                    positionSize,
                    riskAmount,
                    notionalValue,
                    false,
                    "Position size below exchange minimum ($" + MIN_POSITION_SIZE + ")"
            );
        }

        // Check maximum position size and cap if necessary
        if (notionalValue.compareTo(MAX_POSITION_SIZE) > 0) {
            // Cap at maximum position size
            BigDecimal cappedNotionalValue = MAX_POSITION_SIZE;
            return new PositionSizeResult(
                    positionSize,
                    riskAmount,
                    cappedNotionalValue,
                    true,
                    "Position size capped at maximum ($" + MAX_POSITION_SIZE + ")"
            );
        }

        // Position size is valid
        return new PositionSizeResult(
                positionSize,
                riskAmount,
                notionalValue,
                true,
                "Position size valid"
        );
    }
}
