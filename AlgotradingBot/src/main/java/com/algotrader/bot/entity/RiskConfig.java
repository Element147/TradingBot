package com.algotrader.bot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_config")
public class RiskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal maxRiskPerTrade;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal maxDailyLossLimit;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal maxDrawdownLimit;

    @NotNull
    @Column(nullable = false)
    private Integer maxOpenPositions;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal correlationLimit;

    @NotNull
    @Column(nullable = false)
    private Boolean circuitBreakerActive;

    @Column(length = 255)
    private String circuitBreakerReason;

    @Column
    private LocalDateTime circuitBreakerTriggeredAt;

    @Column
    private LocalDateTime circuitBreakerOverriddenAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (circuitBreakerActive == null) {
            circuitBreakerActive = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public RiskConfig() {
    }

    public RiskConfig(BigDecimal maxRiskPerTrade,
                      BigDecimal maxDailyLossLimit,
                      BigDecimal maxDrawdownLimit,
                      Integer maxOpenPositions,
                      BigDecimal correlationLimit) {
        this.maxRiskPerTrade = maxRiskPerTrade;
        this.maxDailyLossLimit = maxDailyLossLimit;
        this.maxDrawdownLimit = maxDrawdownLimit;
        this.maxOpenPositions = maxOpenPositions;
        this.correlationLimit = correlationLimit;
        this.circuitBreakerActive = false;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getMaxRiskPerTrade() {
        return maxRiskPerTrade;
    }

    public void setMaxRiskPerTrade(BigDecimal maxRiskPerTrade) {
        this.maxRiskPerTrade = maxRiskPerTrade;
    }

    public BigDecimal getMaxDailyLossLimit() {
        return maxDailyLossLimit;
    }

    public void setMaxDailyLossLimit(BigDecimal maxDailyLossLimit) {
        this.maxDailyLossLimit = maxDailyLossLimit;
    }

    public BigDecimal getMaxDrawdownLimit() {
        return maxDrawdownLimit;
    }

    public void setMaxDrawdownLimit(BigDecimal maxDrawdownLimit) {
        this.maxDrawdownLimit = maxDrawdownLimit;
    }

    public Integer getMaxOpenPositions() {
        return maxOpenPositions;
    }

    public void setMaxOpenPositions(Integer maxOpenPositions) {
        this.maxOpenPositions = maxOpenPositions;
    }

    public BigDecimal getCorrelationLimit() {
        return correlationLimit;
    }

    public void setCorrelationLimit(BigDecimal correlationLimit) {
        this.correlationLimit = correlationLimit;
    }

    public Boolean getCircuitBreakerActive() {
        return circuitBreakerActive;
    }

    public void setCircuitBreakerActive(Boolean circuitBreakerActive) {
        this.circuitBreakerActive = circuitBreakerActive;
    }

    public String getCircuitBreakerReason() {
        return circuitBreakerReason;
    }

    public void setCircuitBreakerReason(String circuitBreakerReason) {
        this.circuitBreakerReason = circuitBreakerReason;
    }

    public LocalDateTime getCircuitBreakerTriggeredAt() {
        return circuitBreakerTriggeredAt;
    }

    public void setCircuitBreakerTriggeredAt(LocalDateTime circuitBreakerTriggeredAt) {
        this.circuitBreakerTriggeredAt = circuitBreakerTriggeredAt;
    }

    public LocalDateTime getCircuitBreakerOverriddenAt() {
        return circuitBreakerOverriddenAt;
    }

    public void setCircuitBreakerOverriddenAt(LocalDateTime circuitBreakerOverriddenAt) {
        this.circuitBreakerOverriddenAt = circuitBreakerOverriddenAt;
    }
}
