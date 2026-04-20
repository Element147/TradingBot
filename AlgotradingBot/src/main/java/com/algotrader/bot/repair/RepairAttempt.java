package com.algotrader.bot.repair;

import com.algotrader.bot.validation.ValidationResult;
import java.time.Duration;
import java.time.LocalDateTime;

public class RepairAttempt {
    private int attemptNumber;
    private ValidationResult triggeringFailure;
    private RepairAction actionTaken;
    private LocalDateTime timestamp;
    private Duration executionTime;
    private RepairResult result;
    private String logOutput;

    public RepairAttempt(int attemptNumber, ValidationResult triggeringFailure, RepairAction actionTaken) {
        this.attemptNumber = attemptNumber;
        this.triggeringFailure = triggeringFailure;
        this.actionTaken = actionTaken;
        this.timestamp = LocalDateTime.now();
    }

    public boolean wasSuccessful() {
        return result != null && result.isSuccessful();
    }

    // Getters and setters
    public int getAttemptNumber() { return attemptNumber; }
    public ValidationResult getTriggeringFailure() { return triggeringFailure; }
    public RepairAction getActionTaken() { return actionTaken; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Duration getExecutionTime() { return executionTime; }
    public void setExecutionTime(Duration executionTime) { this.executionTime = executionTime; }
    public RepairResult getResult() { return result; }
    public void setResult(RepairResult result) { this.result = result; }
    public String getLogOutput() { return logOutput; }
    public void setLogOutput(String logOutput) { this.logOutput = logOutput; }
}
