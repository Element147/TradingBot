package com.algotrader.bot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a trade execution in the algorithmic trading system.
 * All monetary values use BigDecimal for precision in financial calculations.
 */
@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trade_account", columnList = "account_id"),
    @Index(name = "idx_trade_symbol", columnList = "symbol"),
    @Index(name = "idx_trade_entry_time", columnList = "entry_time"),
    @Index(name = "idx_trade_exit_time", columnList = "exit_time")
})
public class Trade {

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
    private SignalType signalType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide positionSide = PositionSide.LONG;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime entryTime;

    @Column
    private LocalDateTime exitTime;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal positionSize;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal riskAmount;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal takeProfit;

    @Column(precision = 20, scale = 8)
    private BigDecimal pnl;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal actualFees;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal actualSlippage;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    /**
     * Signal type enum for trade direction
     */
    public enum SignalType {
        BUY,
        SELL,
        SHORT,
        COVER,
        HOLD
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public Trade() {
    }

    public Trade(Long accountId, String symbol, SignalType signalType, LocalDateTime entryTime,
                 BigDecimal entryPrice, BigDecimal positionSize, BigDecimal riskAmount,
                 BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal actualFees,
                 BigDecimal actualSlippage) {
        this(accountId, symbol, signalType, PositionSide.LONG, entryTime, entryPrice, positionSize, riskAmount,
            stopLoss, takeProfit, actualFees, actualSlippage);
    }

    public Trade(Long accountId,
                 String symbol,
                 SignalType signalType,
                 PositionSide positionSide,
                 LocalDateTime entryTime,
                 BigDecimal entryPrice, BigDecimal positionSize, BigDecimal riskAmount,
                 BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal actualFees,
                 BigDecimal actualSlippage) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.signalType = signalType;
        this.positionSide = positionSide;
        this.entryTime = entryTime;
        this.entryPrice = entryPrice;
        this.positionSize = positionSize;
        this.riskAmount = riskAmount;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.actualFees = actualFees;
        this.actualSlippage = actualSlippage;
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

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }

    public PositionSide getPositionSide() {
        return positionSide;
    }

    public void setPositionSide(PositionSide positionSide) {
        this.positionSide = positionSide;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    public BigDecimal getPositionSize() {
        return positionSize;
    }

    public void setPositionSize(BigDecimal positionSize) {
        this.positionSize = positionSize;
    }

    public BigDecimal getRiskAmount() {
        return riskAmount;
    }

    public void setRiskAmount(BigDecimal riskAmount) {
        this.riskAmount = riskAmount;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(BigDecimal takeProfit) {
        this.takeProfit = takeProfit;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public BigDecimal getActualFees() {
        return actualFees;
    }

    public void setActualFees(BigDecimal actualFees) {
        this.actualFees = actualFees;
    }

    public BigDecimal getActualSlippage() {
        return actualSlippage;
    }

    public void setActualSlippage(BigDecimal actualSlippage) {
        this.actualSlippage = actualSlippage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "id=" + id +
                ", accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", signalType=" + signalType +
                ", positionSide=" + positionSide +
                ", entryTime=" + entryTime +
                ", exitTime=" + exitTime +
                ", entryPrice=" + entryPrice +
                ", exitPrice=" + exitPrice +
                ", positionSize=" + positionSize +
                ", riskAmount=" + riskAmount +
                ", stopLoss=" + stopLoss +
                ", takeProfit=" + takeProfit +
                ", pnl=" + pnl +
                ", actualFees=" + actualFees +
                ", actualSlippage=" + actualSlippage +
                '}';
    }
}
