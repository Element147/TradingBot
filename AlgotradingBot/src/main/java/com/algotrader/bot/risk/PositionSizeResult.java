package com.algotrader.bot.risk;

import java.math.BigDecimal;

/**
 * DTO containing position sizing calculation results.
 * All monetary values use BigDecimal for precision.
 */
public class PositionSizeResult {
    private final BigDecimal positionSize;
    private final BigDecimal riskAmount;
    private final BigDecimal notionalValue;
    private final boolean isValid;
    private final String validationMessage;

    public PositionSizeResult(BigDecimal positionSize, BigDecimal riskAmount, 
                             BigDecimal notionalValue, boolean isValid, String validationMessage) {
        this.positionSize = positionSize;
        this.riskAmount = riskAmount;
        this.notionalValue = notionalValue;
        this.isValid = isValid;
        this.validationMessage = validationMessage;
    }

    public BigDecimal getPositionSize() {
        return positionSize;
    }

    public BigDecimal getRiskAmount() {
        return riskAmount;
    }

    public BigDecimal getNotionalValue() {
        return notionalValue;
    }

    public boolean isValid() {
        return isValid;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    @Override
    public String toString() {
        return "PositionSizeResult{" +
                "positionSize=" + positionSize +
                ", riskAmount=" + riskAmount +
                ", notionalValue=" + notionalValue +
                ", isValid=" + isValid +
                ", validationMessage='" + validationMessage + '\'' +
                '}';
    }
}
