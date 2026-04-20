package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_config_versions", indexes = {
    @Index(name = "idx_strategy_cfg_version_strategy", columnList = "strategy_config_id"),
    @Index(name = "idx_strategy_cfg_version_changed_at", columnList = "changed_at")
})
public class StrategyConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "strategy_config_id", nullable = false)
    private Long strategyConfigId;

    @NotNull
    @Positive
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "change_reason", nullable = false, length = 255)
    private String changeReason;

    @NotNull
    @Size(min = 3, max = 20)
    @Column(nullable = false, length = 20)
    private String symbol;

    @NotNull
    @Size(min = 1, max = 10)
    @Column(nullable = false, length = 10)
    private String timeframe;

    @NotNull
    @Column(name = "risk_per_trade", nullable = false, precision = 6, scale = 4)
    private BigDecimal riskPerTrade;

    @NotNull
    @Column(name = "min_position_size", nullable = false, precision = 20, scale = 8)
    private BigDecimal minPositionSize;

    @NotNull
    @Column(name = "max_position_size", nullable = false, precision = 20, scale = 8)
    private BigDecimal maxPositionSize;

    @NotNull
    @Size(min = 1, max = 20)
    @Column(nullable = false, length = 20)
    private String status;

    @NotNull
    @Column(name = "paper_mode", nullable = false)
    private Boolean paperMode;

    @NotNull
    @Column(name = "short_selling_enabled", nullable = false)
    private Boolean shortSellingEnabled = Boolean.FALSE;

    @NotNull
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static StrategyConfigVersion fromStrategy(StrategyConfig strategy, int versionNumber, String changeReason) {
        StrategyConfigVersion version = new StrategyConfigVersion();
        version.strategyConfigId = strategy.getId();
        version.versionNumber = versionNumber;
        version.changeReason = changeReason;
        version.symbol = strategy.getSymbol();
        version.timeframe = strategy.getTimeframe();
        version.riskPerTrade = strategy.getRiskPerTrade();
        version.minPositionSize = strategy.getMinPositionSize();
        version.maxPositionSize = strategy.getMaxPositionSize();
        version.status = strategy.getStatus().name();
        version.paperMode = strategy.getPaperMode();
        version.shortSellingEnabled = strategy.getShortSellingEnabled();
        version.changedAt = LocalDateTime.now();
        return version;
    }

    public Long getId() {
        return id;
    }

    public Long getStrategyConfigId() {
        return strategyConfigId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public BigDecimal getRiskPerTrade() {
        return riskPerTrade;
    }

    public BigDecimal getMinPositionSize() {
        return minPositionSize;
    }

    public BigDecimal getMaxPositionSize() {
        return maxPositionSize;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getPaperMode() {
        return paperMode;
    }

    public Boolean getShortSellingEnabled() {
        return shortSellingEnabled;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
