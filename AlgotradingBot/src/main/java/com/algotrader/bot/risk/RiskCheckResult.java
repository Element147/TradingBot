package com.algotrader.bot.risk;

import java.math.BigDecimal;

/**
 * DTO representing the result of a risk check operation.
 * Contains information about whether trading is allowed and the reason.
 */
public class RiskCheckResult {

    private final boolean canTrade;
    private final String reason;
    private final BigDecimal currentDrawdown;
    private final BigDecimal sharpeRatio;

    public RiskCheckResult(boolean canTrade, String reason, BigDecimal currentDrawdown, BigDecimal sharpeRatio) {
        this.canTrade = canTrade;
        this.reason = reason;
        this.currentDrawdown = currentDrawdown;
        this.sharpeRatio = sharpeRatio;
    }

    public boolean isCanTrade() {
        return canTrade;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getCurrentDrawdown() {
        return currentDrawdown;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    @Override
    public String toString() {
        return "RiskCheckResult{" +
                "canTrade=" + canTrade +
                ", reason='" + reason + '\'' +
                ", currentDrawdown=" + currentDrawdown +
                ", sharpeRatio=" + sharpeRatio +
                '}';
    }
}
