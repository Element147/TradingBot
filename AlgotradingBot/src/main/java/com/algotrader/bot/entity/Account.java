package com.algotrader.bot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a trading account in the algorithmic trading system.
 * Manages account balance, risk parameters, and portfolio positions.
 * All monetary values use BigDecimal for precision in financial calculations.
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_status", columnList = "status"),
    @Index(name = "idx_account_created", columnList = "created_at")
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal initialBalance;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal currentBalance;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal totalPnl;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal riskPerTrade;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal maxDrawdownLimit;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @OneToMany(mappedBy = "accountId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Portfolio> portfolios = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    /**
     * Account status enum for trading account state management
     */
    public enum AccountStatus {
        ACTIVE,
        STOPPED,
        CIRCUIT_BREAKER_TRIGGERED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalPnl == null) {
            totalPnl = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public Account() {
    }

    public Account(BigDecimal initialBalance, BigDecimal riskPerTrade, BigDecimal maxDrawdownLimit) {
        this.initialBalance = initialBalance;
        this.currentBalance = initialBalance;
        this.totalPnl = BigDecimal.ZERO;
        this.riskPerTrade = riskPerTrade;
        this.maxDrawdownLimit = maxDrawdownLimit;
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Calculates current drawdown percentage from initial balance.
     * Formula: ((initialBalance - currentBalance) / initialBalance) * 100
     *
     * @return current drawdown percentage as BigDecimal
     */
    public BigDecimal getCurrentDrawdown() {
        if (initialBalance == null || currentBalance == null || initialBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return initialBalance.subtract(currentBalance)
                .divide(initialBalance, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculates total return percentage.
     * Formula: ((currentBalance - initialBalance) / initialBalance) * 100
     *
     * @return total return percentage as BigDecimal
     */
    public BigDecimal getTotalReturnPercentage() {
        if (initialBalance == null || currentBalance == null || initialBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentBalance.subtract(initialBalance)
                .divide(initialBalance, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Checks if account has exceeded max drawdown limit.
     *
     * @return true if current drawdown exceeds max drawdown limit
     */
    public boolean isDrawdownLimitExceeded() {
        return getCurrentDrawdown().compareTo(maxDrawdownLimit) > 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public BigDecimal getTotalPnl() {
        return totalPnl;
    }

    public void setTotalPnl(BigDecimal totalPnl) {
        this.totalPnl = totalPnl;
    }

    public BigDecimal getRiskPerTrade() {
        return riskPerTrade;
    }

    public void setRiskPerTrade(BigDecimal riskPerTrade) {
        this.riskPerTrade = riskPerTrade;
    }

    public BigDecimal getMaxDrawdownLimit() {
        return maxDrawdownLimit;
    }

    public void setMaxDrawdownLimit(BigDecimal maxDrawdownLimit) {
        this.maxDrawdownLimit = maxDrawdownLimit;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public List<Portfolio> getPortfolios() {
        return portfolios;
    }

    public void setPortfolios(List<Portfolio> portfolios) {
        this.portfolios = portfolios;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", initialBalance=" + initialBalance +
                ", currentBalance=" + currentBalance +
                ", totalPnl=" + totalPnl +
                ", riskPerTrade=" + riskPerTrade +
                ", maxDrawdownLimit=" + maxDrawdownLimit +
                ", status=" + status +
                ", currentDrawdown=" + getCurrentDrawdown() +
                ", totalReturnPercentage=" + getTotalReturnPercentage() +
                '}';
    }
}
