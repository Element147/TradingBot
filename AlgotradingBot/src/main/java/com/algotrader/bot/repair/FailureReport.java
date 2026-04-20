package com.algotrader.bot.repair;

import com.algotrader.bot.validation.ValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FailureReport {
    private ValidationResult originalFailure;
    private List<RepairAttempt> repairAttempts;
    private LocalDateTime timestamp;
    private String environment;
    private Map<String, String> systemInfo;
    private List<String> diagnosticLogs;

    public FailureReport(ValidationResult originalFailure) {
        this.originalFailure = originalFailure;
        this.repairAttempts = new ArrayList<>();
        this.timestamp = LocalDateTime.now();
        this.systemInfo = new HashMap<>();
        this.diagnosticLogs = new ArrayList<>();
    }

    public String generateDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== FAILURE REPORT ===\n");
        report.append("Generated: ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        report.append("Environment: ").append(environment != null ? environment : "Unknown").append("\n\n");
        
        report.append("ORIGINAL FAILURE:\n");
        report.append("  Requirement: ").append(originalFailure.getRequirementId()).append("\n");
        report.append("  Name: ").append(originalFailure.getRequirementName()).append("\n");
        report.append("  Message: ").append(originalFailure.getMessage()).append("\n\n");
        
        report.append("REPAIR ATTEMPTS (").append(repairAttempts.size()).append("):\n");
        for (RepairAttempt attempt : repairAttempts) {
            report.append("  Attempt #").append(attempt.getAttemptNumber()).append(":\n");
            report.append("    Action: ").append(attempt.getActionTaken()).append("\n");
            report.append("    Time: ").append(attempt.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_TIME)).append("\n");
            report.append("    Success: ").append(attempt.wasSuccessful()).append("\n");
            if (attempt.getResult() != null) {
                report.append("    Message: ").append(attempt.getResult().getMessage()).append("\n");
            }
        }
        
        if (!diagnosticLogs.isEmpty()) {
            report.append("\nDIAGNOSTIC LOGS:\n");
            for (String log : diagnosticLogs) {
                report.append("  ").append(log).append("\n");
            }
        }
        
        return report.toString();
    }

    public void saveToFile(Path outputPath) throws IOException {
        String report = generateDetailedReport();
        Files.writeString(outputPath, report);
    }

    // Getters and setters
    public ValidationResult getOriginalFailure() { return originalFailure; }
    public List<RepairAttempt> getRepairAttempts() { return repairAttempts; }
    public void addRepairAttempt(RepairAttempt attempt) { this.repairAttempts.add(attempt); }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Map<String, String> getSystemInfo() { return systemInfo; }
    public void addSystemInfo(String key, String value) { this.systemInfo.put(key, value); }
    public List<String> getDiagnosticLogs() { return diagnosticLogs; }
    public void addDiagnosticLog(String log) { this.diagnosticLogs.add(log); }
}
