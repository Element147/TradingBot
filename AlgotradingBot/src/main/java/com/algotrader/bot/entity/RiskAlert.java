package com.algotrader.bot.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_alerts", indexes = {
    @Index(name = "idx_risk_alert_timestamp", columnList = "timestamp")
})
public class RiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(nullable = false, length = 100)
    private String actionTaken;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public RiskAlert() {
    }

    public RiskAlert(String type, String severity, String message, String actionTaken) {
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.actionTaken = actionTaken;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
