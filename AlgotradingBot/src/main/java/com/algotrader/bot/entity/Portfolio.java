package com.algotrader.bot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * JPA entity representing a portfolio position in the algorithmic trading system.
 * Tracks current holdings, entry prices, and unrealized PnL for each symbol.
 * All monetary values use BigDecimal for precision in financial calculations.
 */
@Entity
@Table(name = "portfolio", indexes = {
    @Index(name = "idx_portfolio_account", columnList = "account_id"),
    @Index(name = "idx_portfolio_symbol", columnList = "symbol"),
    @Index(name = "idx_portfolio_updated", columnList = "last_updated")
})
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Long accountId;

    @NotNull
    @Size(min = 3, max = 20)
    @Column(nullable = false, length = 20)
    private String symbol;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide positionSide = PositionSide.LONG;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal positionSize;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal averageEntryPrice;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal currentPrice;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastUpdated == null) {
            lastUpdated = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    // Constructors
    public Portfolio() {
    }

    public Portfolio(Long accountId, String symbol, BigDecimal positionSize,
                     BigDecimal averageEntryPrice, BigDecimal currentPrice) {
        this(accountId, symbol, positionSize, averageEntryPrice, currentPrice, PositionSide.LONG);
    }

    public Portfolio(Long accountId,
                     String symbol,
                     BigDecimal positionSize,
                     BigDecimal averageEntryPrice,
                     BigDecimal currentPrice,
                     PositionSide positionSide) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.positionSide = positionSide;
        this.positionSize = positionSize;
        this.averageEntryPrice = averageEntryPrice;
        this.currentPrice = currentPrice;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Calculates unrealized PnL based on current price vs average entry price.
     * Formula: (currentPrice - averageEntryPrice) * positionSize
     *
     * @return unrealized profit or loss as BigDecimal
     */
    public BigDecimal getUnrealizedPnl() {
        if (currentPrice == null || averageEntryPrice == null || positionSize == null) {
            return BigDecimal.ZERO;
        }
        if (positionSide == null || positionSide.isLong()) {
            return currentPrice.subtract(averageEntryPrice).multiply(positionSize);
        }
        return averageEntryPrice.subtract(currentPrice).multiply(positionSize);
    }

    /**
     * Calculates unrealized PnL percentage.
     * Formula: ((currentPrice - averageEntryPrice) / averageEntryPrice) * 100
     *
     * @return unrealized PnL percentage as BigDecimal
     */
    public BigDecimal getUnrealizedPnlPercentage() {
        if (currentPrice == null || averageEntryPrice == null || averageEntryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal priceChange = positionSide == null || positionSide.isLong()
            ? currentPrice.subtract(averageEntryPrice)
            : averageEntryPrice.subtract(currentPrice);
        return priceChange
                .divide(averageEntryPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculates the current equity contribution of this position.
     * Long positions use current mark-to-market value. Short positions use
     * reserved collateral plus unrealized PnL.
     *
     * @return current position value as BigDecimal
     */
    public BigDecimal getPositionValue() {
        return getEquityContribution();
    }

    public BigDecimal getEntryNotional() {
        if (averageEntryPrice == null || positionSize == null) {
            return BigDecimal.ZERO;
        }
        return averageEntryPrice.multiply(positionSize);
    }

    public BigDecimal getMarketExposure() {
        if (currentPrice == null || positionSize == null) {
            return BigDecimal.ZERO;
        }
        return currentPrice.multiply(positionSize);
    }

    public BigDecimal getEquityContribution() {
        if (positionSide == null || positionSide.isLong()) {
            return getMarketExposure();
        }
        return getEntryNotional().add(getUnrealizedPnl());
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public PositionSide getPositionSide() {
        return positionSide;
    }

    public void setPositionSide(PositionSide positionSide) {
        this.positionSide = positionSide;
    }

    public BigDecimal getPositionSize() {
        return positionSize;
    }

    public void setPositionSize(BigDecimal positionSize) {
        this.positionSize = positionSize;
    }

    public BigDecimal getAverageEntryPrice() {
        return averageEntryPrice;
    }

    public void setAverageEntryPrice(BigDecimal averageEntryPrice) {
        this.averageEntryPrice = averageEntryPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Portfolio{" +
                "id=" + id +
                ", accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", positionSide=" + positionSide +
                ", positionSize=" + positionSize +
                ", averageEntryPrice=" + averageEntryPrice +
                ", currentPrice=" + currentPrice +
                ", unrealizedPnl=" + getUnrealizedPnl() +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
