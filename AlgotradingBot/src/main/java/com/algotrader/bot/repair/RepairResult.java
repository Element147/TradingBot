package com.algotrader.bot.repair;

import com.algotrader.bot.validation.ValidationResult;

public record RepairResult(boolean successful, String message, ValidationResult retryResult) {

    public RepairResult(boolean successful, String message) {
        this(successful, message, null);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getMessage() {
        return message;
    }

    public ValidationResult getRetryResult() {
        return retryResult;
    }

    public RepairResult withRetryResult(ValidationResult retryResult) {
        return new RepairResult(successful, message, retryResult);
    }
}
