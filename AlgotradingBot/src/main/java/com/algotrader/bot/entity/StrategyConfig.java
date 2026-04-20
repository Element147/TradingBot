package com.algotrader.bot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted strategy configuration and current runtime state.
 */
@Entity
@Table(name = "strategy_configs", indexes = {
    @Index(name = "idx_strategy_config_name", columnList = "name", unique = true),
    @Index(name = "idx_strategy_config_status", columnList = "status")
})
public class StrategyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 3, max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @NotNull
    @Size(min = 3, max = 50)
    @Column(nullable = false, length = 50)
    private String type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StrategyStatus status;

    @NotNull
    @Size(min = 3, max = 20)
    @Column(nullable = false, length = 20)
    private String symbol;

    @NotNull
    @Size(min = 1, max = 10)
    @Column(nullable = false, length = 10)
    private String timeframe;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal riskPerTrade;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal minPositionSize;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal maxPositionSize;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal profitLoss;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer tradeCount;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal currentDrawdown;

    @NotNull
    @Column(nullable = false)
    private Boolean paperMode;

    @NotNull
    @Column(nullable = false)
    private Boolean shortSellingEnabled = Boolean.FALSE;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime stoppedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    public enum StrategyStatus {
        RUNNING,
        STOPPED,
        ERROR
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = StrategyStatus.STOPPED;
        }
        if (profitLoss == null) {
            profitLoss = BigDecimal.ZERO;
        }
        if (tradeCount == null) {
            tradeCount = 0;
        }
        if (currentDrawdown == null) {
            currentDrawdown = BigDecimal.ZERO;
        }
        if (paperMode == null) {
            paperMode = Boolean.TRUE;
        }
        if (shortSellingEnabled == null) {
            shortSellingEnabled = Boolean.FALSE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public StrategyConfig() {
    }

    public StrategyConfig(String name,
                          String type,
                          String symbol,
                          String timeframe,
                          BigDecimal riskPerTrade,
                          BigDecimal minPositionSize,
                          BigDecimal maxPositionSize) {
        this.name = name;
        this.type = type;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.riskPerTrade = riskPerTrade;
        this.minPositionSize = minPositionSize;
        this.maxPositionSize = maxPositionSize;
        this.status = StrategyStatus.STOPPED;
        this.profitLoss = BigDecimal.ZERO;
        this.tradeCount = 0;
        this.currentDrawdown = BigDecimal.ZERO;
        this.paperMode = Boolean.TRUE;
        this.shortSellingEnabled = Boolean.FALSE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyStatus status) {
        this.status = status;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public BigDecimal getRiskPerTrade() {
        return riskPerTrade;
    }

    public void setRiskPerTrade(BigDecimal riskPerTrade) {
        this.riskPerTrade = riskPerTrade;
    }

    public BigDecimal getMinPositionSize() {
        return minPositionSize;
    }

    public void setMinPositionSize(BigDecimal minPositionSize) {
        this.minPositionSize = minPositionSize;
    }

    public BigDecimal getMaxPositionSize() {
        return maxPositionSize;
    }

    public void setMaxPositionSize(BigDecimal maxPositionSize) {
        this.maxPositionSize = maxPositionSize;
    }

    public BigDecimal getProfitLoss() {
        return profitLoss;
    }

    public void setProfitLoss(BigDecimal profitLoss) {
        this.profitLoss = profitLoss;
    }

    public Integer getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(Integer tradeCount) {
        this.tradeCount = tradeCount;
    }

    public BigDecimal getCurrentDrawdown() {
        return currentDrawdown;
    }

    public void setCurrentDrawdown(BigDecimal currentDrawdown) {
        this.currentDrawdown = currentDrawdown;
    }

    public Boolean getPaperMode() {
        return paperMode;
    }

    public void setPaperMode(Boolean paperMode) {
        this.paperMode = paperMode;
    }

    public Boolean getShortSellingEnabled() {
        return shortSellingEnabled;
    }

    public void setShortSellingEnabled(Boolean shortSellingEnabled) {
        this.shortSellingEnabled = shortSellingEnabled;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(LocalDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
