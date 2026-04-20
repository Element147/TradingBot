package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "backtest_trade_series", indexes = {
    @Index(name = "idx_backtest_trade_series_backtest", columnList = "backtest_result_id"),
    @Index(name = "idx_backtest_trade_series_entry_time", columnList = "entry_time"),
    @Index(name = "idx_backtest_trade_series_exit_time", columnList = "exit_time")
})
public class BacktestTradeSeriesItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backtest_result_id", nullable = false)
    private BacktestResult backtestResult;

    @NotNull
    @Size(min = 1, max = 20)
    @Column(nullable = false, length = 20)
    private String symbol;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "position_side", nullable = false, length = 10)
    private PositionSide positionSide = PositionSide.LONG;

    @NotNull
    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @NotNull
    @Column(name = "exit_time", nullable = false)
    private LocalDateTime exitTime;

    @NotNull
    @Column(name = "entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @NotNull
    @Column(name = "exit_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @NotNull
    @Column(name = "entry_value", nullable = false, precision = 20, scale = 8)
    private BigDecimal entryValue;

    @NotNull
    @Column(name = "exit_value", nullable = false, precision = 20, scale = 8)
    private BigDecimal exitValue;

    @NotNull
    @Column(name = "return_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal returnPct;

    public Long getId() {
        return id;
    }

    public BacktestResult getBacktestResult() {
        return backtestResult;
    }

    public void setBacktestResult(BacktestResult backtestResult) {
        this.backtestResult = backtestResult;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getEntryValue() {
        return entryValue;
    }

    public void setEntryValue(BigDecimal entryValue) {
        this.entryValue = entryValue;
    }

    public BigDecimal getExitValue() {
        return exitValue;
    }

    public void setExitValue(BigDecimal exitValue) {
        this.exitValue = exitValue;
    }

    public BigDecimal getReturnPct() {
        return returnPct;
    }

    public void setReturnPct(BigDecimal returnPct) {
        this.returnPct = returnPct;
    }
}
