package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "operator_audit_events", indexes = {
    @Index(name = "idx_operator_audit_created_at", columnList = "created_at"),
    @Index(name = "idx_operator_audit_action", columnList = "action"),
    @Index(name = "idx_operator_audit_environment", columnList = "environment")
})
public class OperatorAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    @Column(nullable = false, length = 100)
    private String actor;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(nullable = false, length = 80)
    private String action;

    @NotNull
    @Size(min = 1, max = 20)
    @Column(nullable = false, length = 20)
    private String environment;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Size(max = 120)
    @Column(name = "target_id", length = 120)
    private String targetId;

    @NotNull
    @Size(min = 1, max = 20)
    @Column(nullable = false, length = 20)
    private String outcome;

    @Size(max = 2000)
    @Column(length = 2000)
    private String details;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
