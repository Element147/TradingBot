package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "backtest_equity_points", indexes = {
    @Index(name = "idx_backtest_equity_backtest", columnList = "backtest_result_id"),
    @Index(name = "idx_backtest_equity_timestamp", columnList = "point_timestamp")
})
public class BacktestEquityPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backtest_result_id", nullable = false)
    private BacktestResult backtestResult;

    @NotNull
    @Column(name = "point_timestamp", nullable = false)
    private LocalDateTime pointTimestamp;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal equity;

    @NotNull
    @Column(name = "drawdown_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal drawdownPct;

    public Long getId() {
        return id;
    }

    public BacktestResult getBacktestResult() {
        return backtestResult;
    }

    public void setBacktestResult(BacktestResult backtestResult) {
        this.backtestResult = backtestResult;
    }

    public LocalDateTime getPointTimestamp() {
        return pointTimestamp;
    }

    public void setPointTimestamp(LocalDateTime pointTimestamp) {
        this.pointTimestamp = pointTimestamp;
    }

    public BigDecimal getEquity() {
        return equity;
    }

    public void setEquity(BigDecimal equity) {
        this.equity = equity;
    }

    public BigDecimal getDrawdownPct() {
        return drawdownPct;
    }

    public void setDrawdownPct(BigDecimal drawdownPct) {
        this.drawdownPct = drawdownPct;
    }
}
