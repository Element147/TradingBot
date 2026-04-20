package com.algotrader.bot.risk;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates realistic transaction costs including exchange fees and slippage.
 * Uses BigDecimal for all monetary calculations to ensure precision.
 * 
 * Cost Structure:
 * - Taker fee: 0.1% (0.001)
 * - Slippage: 0.03% (0.0003) - 3 basis points
 * - Total cost multiplier: 1.0013 for buys, 0.9987 for sells
 */
@Component
public class SlippageCalculator {

    // Taker fee: 0.1%
    private static final BigDecimal TAKER_FEE_RATE = new BigDecimal("0.001");
    
    // Slippage: 0.03% (3 basis points)
    private static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.0003");
    
    // Combined cost rate: 0.1% + 0.03% = 0.13%
    private static final BigDecimal TOTAL_COST_RATE = TAKER_FEE_RATE.add(SLIPPAGE_RATE);
    
    // Scale for BigDecimal calculations
    private static final int SCALE = 8;

    /**
     * Calculate real transaction costs including fees and slippage.
     * 
     * For buys: effective price = price * (1 + 0.0013) = price * 1.0013
     * For sells: effective price = price * (1 - 0.0013) = price * 0.9987
     * 
     * @param price The quoted price for the asset
     * @param quantity The quantity of the asset being traded
     * @param isBuy True for buy orders, false for sell orders
     * @return TransactionCost containing effective price, fees, slippage, and net cost
     * @throws IllegalArgumentException if inputs are invalid
     */
    public TransactionCost calculateRealCost(BigDecimal price, BigDecimal quantity, boolean isBuy) {
        // Validate inputs
        validateInputs(price, quantity);

        // Calculate notional value (price * quantity)
        BigDecimal notionalValue = price.multiply(quantity)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Calculate fees (0.1% of notional value)
        BigDecimal totalFees = notionalValue.multiply(TAKER_FEE_RATE)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Calculate slippage (0.03% of notional value)
        BigDecimal totalSlippage = notionalValue.multiply(SLIPPAGE_RATE)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Calculate effective price and net cost
        BigDecimal effectivePrice;
        BigDecimal netCost;

        if (isBuy) {
            // For buys: price increases by total cost rate
            effectivePrice = price.multiply(BigDecimal.ONE.add(TOTAL_COST_RATE))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            
            // Net cost = notional value + fees + slippage
            netCost = notionalValue.add(totalFees).add(totalSlippage)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            // For sells: price decreases by total cost rate
            effectivePrice = price.multiply(BigDecimal.ONE.subtract(TOTAL_COST_RATE))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            
            // Net cost = notional value - fees - slippage (revenue received)
            netCost = notionalValue.subtract(totalFees).subtract(totalSlippage)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        return new TransactionCost(effectivePrice, totalFees, totalSlippage, netCost);
    }

    /**
     * Validate input parameters.
     */
    private void validateInputs(BigDecimal price, BigDecimal quantity) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
