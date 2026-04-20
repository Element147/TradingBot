package com.algotrader.bot.risk;

import java.math.BigDecimal;

/**
 * DTO containing transaction cost calculation results.
 * Tracks fees and slippage separately for reporting and analysis.
 * All monetary values use BigDecimal for precision.
 */
public class TransactionCost {
    private final BigDecimal effectivePrice;
    private final BigDecimal totalFees;
    private final BigDecimal totalSlippage;
    private final BigDecimal netCost;

    public TransactionCost(BigDecimal effectivePrice, BigDecimal totalFees,
                          BigDecimal totalSlippage, BigDecimal netCost) {
        this.effectivePrice = effectivePrice;
        this.totalFees = totalFees;
        this.totalSlippage = totalSlippage;
        this.netCost = netCost;
    }

    public BigDecimal getEffectivePrice() {
        return effectivePrice;
    }

    public BigDecimal getTotalFees() {
        return totalFees;
    }

    public BigDecimal getTotalSlippage() {
        return totalSlippage;
    }

    public BigDecimal getNetCost() {
        return netCost;
    }

    @Override
    public String toString() {
        return "TransactionCost{" +
                "effectivePrice=" + effectivePrice +
                ", totalFees=" + totalFees +
                ", totalSlippage=" + totalSlippage +
                ", netCost=" + netCost +
                '}';
    }
}
