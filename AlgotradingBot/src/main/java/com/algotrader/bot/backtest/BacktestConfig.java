package com.algotrader.bot.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration DTO for backtest execution.
 * Uses builder pattern for immutability and ease of construction.
 * All monetary values use BigDecimal for precision.
 */
public class BacktestConfig {

    private final String symbol;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final BigDecimal initialBalance;
    private final BigDecimal riskPerTrade;
    private final BigDecimal maxDrawdownLimit;
    private final BigDecimal commissionRate;
    private final BigDecimal slippageRate;
    private final Map<String, Object> strategyParameters;

    private BacktestConfig(Builder builder) {
        this.symbol = builder.symbol;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.initialBalance = builder.initialBalance;
        this.riskPerTrade = builder.riskPerTrade;
        this.maxDrawdownLimit = builder.maxDrawdownLimit;
        this.commissionRate = builder.commissionRate;
        this.slippageRate = builder.slippageRate;
        this.strategyParameters = new HashMap<>(builder.strategyParameters);
    }

    /**
     * Validates the configuration parameters.
     * 
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        
        if (endDate == null) {
            throw new IllegalArgumentException("End date cannot be null");
        }
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial balance must be positive");
        }
        
        if (riskPerTrade == null || riskPerTrade.compareTo(BigDecimal.ZERO) <= 0 || 
            riskPerTrade.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Risk per trade must be between 0 and 1");
        }
        
        if (maxDrawdownLimit == null || maxDrawdownLimit.compareTo(BigDecimal.ZERO) <= 0 || 
            maxDrawdownLimit.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Max drawdown limit must be between 0 and 1");
        }
        
        if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Commission rate cannot be negative");
        }
        
        if (slippageRate == null || slippageRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Slippage rate cannot be negative");
        }
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public BigDecimal getRiskPerTrade() {
        return riskPerTrade;
    }

    public BigDecimal getMaxDrawdownLimit() {
        return maxDrawdownLimit;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getSlippageRate() {
        return slippageRate;
    }

    public Map<String, Object> getStrategyParameters() {
        return new HashMap<>(strategyParameters);
    }

    @Override
    public String toString() {
        return "BacktestConfig{" +
                "symbol='" + symbol + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", initialBalance=" + initialBalance +
                ", riskPerTrade=" + riskPerTrade +
                ", maxDrawdownLimit=" + maxDrawdownLimit +
                ", commissionRate=" + commissionRate +
                ", slippageRate=" + slippageRate +
                ", strategyParameters=" + strategyParameters +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BacktestConfig that = (BacktestConfig) o;
        return Objects.equals(symbol, that.symbol) &&
                Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate) &&
                Objects.equals(initialBalance, that.initialBalance) &&
                Objects.equals(riskPerTrade, that.riskPerTrade) &&
                Objects.equals(maxDrawdownLimit, that.maxDrawdownLimit) &&
                Objects.equals(commissionRate, that.commissionRate) &&
                Objects.equals(slippageRate, that.slippageRate) &&
                Objects.equals(strategyParameters, that.strategyParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, startDate, endDate, initialBalance, riskPerTrade,
                maxDrawdownLimit, commissionRate, slippageRate, strategyParameters);
    }

    /**
     * Builder for BacktestConfig with sensible defaults.
     */
    public static class Builder {
        private String symbol;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private BigDecimal initialBalance;
        private BigDecimal riskPerTrade = new BigDecimal("0.02"); // 2% default
        private BigDecimal maxDrawdownLimit = new BigDecimal("0.25"); // 25% default
        private BigDecimal commissionRate = new BigDecimal("0.001"); // 0.1% default
        private BigDecimal slippageRate = new BigDecimal("0.0003"); // 0.03% default
        private Map<String, Object> strategyParameters = new HashMap<>();

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder startDate(LocalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDateTime endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder initialBalance(BigDecimal initialBalance) {
            this.initialBalance = initialBalance;
            return this;
        }

        public Builder riskPerTrade(BigDecimal riskPerTrade) {
            this.riskPerTrade = riskPerTrade;
            return this;
        }

        public Builder maxDrawdownLimit(BigDecimal maxDrawdownLimit) {
            this.maxDrawdownLimit = maxDrawdownLimit;
            return this;
        }

        public Builder commissionRate(BigDecimal commissionRate) {
            this.commissionRate = commissionRate;
            return this;
        }

        public Builder slippageRate(BigDecimal slippageRate) {
            this.slippageRate = slippageRate;
            return this;
        }

        public Builder strategyParameters(Map<String, Object> strategyParameters) {
            this.strategyParameters = new HashMap<>(strategyParameters);
            return this;
        }

        public Builder addStrategyParameter(String key, Object value) {
            this.strategyParameters.put(key, value);
            return this;
        }

        public BacktestConfig build() {
            BacktestConfig config = new BacktestConfig(this);
            config.validate();
            return config;
        }
    }
}
