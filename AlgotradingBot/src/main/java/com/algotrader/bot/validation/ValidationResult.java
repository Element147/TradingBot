package com.algotrader.bot.validation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ValidationResult {
    private final String requirementId;
    private final String requirementName;
    private final ValidationStatus status;
    private final String message;
    private final LocalDateTime timestamp;
    private Duration executionTime;
    private final Map<String, Object> metadata;

    public ValidationResult(String requirementId, String requirementName, ValidationStatus status, String message) {
        this.requirementId = requirementId;
        this.requirementName = requirementName;
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    public boolean isPassed() {
        return status == ValidationStatus.PASSED;
    }

    public boolean isFailed() {
        return status == ValidationStatus.FAILED;
    }

    public String getRequirementId() { return requirementId; }
    public String getRequirementName() { return requirementName; }
    public ValidationStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Duration getExecutionTime() { return executionTime; }
    public void setExecutionTime(Duration executionTime) { this.executionTime = executionTime; }
    
    public Map<String, Object> getMetadata() { return metadata; }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
}
