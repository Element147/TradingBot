package com.algotrader.bot.validation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StabilityMetrics {
    private Duration testDuration;
    private List<HealthCheckResult> healthChecks;
    private List<ResourceSnapshot> resourceSnapshots;
    private int containerRestarts;
    private int errorLogCount;
    private boolean databaseConnectionStable;

    public StabilityMetrics() {
        this.healthChecks = new ArrayList<>();
        this.resourceSnapshots = new ArrayList<>();
        this.containerRestarts = 0;
        this.errorLogCount = 0;
        this.databaseConnectionStable = true;
    }

    public boolean isStable() {
        return containerRestarts == 0 
            && errorLogCount == 0 
            && databaseConnectionStable 
            && healthChecks.stream().allMatch(HealthCheckResult::isHealthy);
    }

    public double getAverageMemoryUsageMB() {
        if (resourceSnapshots.isEmpty()) return 0.0;
        return resourceSnapshots.stream()
            .mapToDouble(snapshot -> snapshot.getTotalMemoryMB())
            .average()
            .orElse(0.0);
    }

    public double getAverageCpuUsagePercent() {
        if (resourceSnapshots.isEmpty()) return 0.0;
        return resourceSnapshots.stream()
            .mapToDouble(snapshot -> snapshot.getAverageCpuPercent())
            .average()
            .orElse(0.0);
    }

    // Getters and setters
    public Duration getTestDuration() { return testDuration; }
    public void setTestDuration(Duration testDuration) { this.testDuration = testDuration; }
    
    public List<HealthCheckResult> getHealthChecks() { return healthChecks; }
    public void addHealthCheck(HealthCheckResult healthCheck) { this.healthChecks.add(healthCheck); }
    
    public List<ResourceSnapshot> getResourceSnapshots() { return resourceSnapshots; }
    public void addResourceSnapshot(ResourceSnapshot snapshot) { this.resourceSnapshots.add(snapshot); }
    
    public int getContainerRestarts() { return containerRestarts; }
    public void setContainerRestarts(int containerRestarts) { this.containerRestarts = containerRestarts; }
    
    public int getErrorLogCount() { return errorLogCount; }
    public void setErrorLogCount(int errorLogCount) { this.errorLogCount = errorLogCount; }
    
    public boolean isDatabaseConnectionStable() { return databaseConnectionStable; }
    public void setDatabaseConnectionStable(boolean stable) { this.databaseConnectionStable = stable; }
    
}

class HealthCheckResult {
    private LocalDateTime timestamp;
    private String serviceName;
    private boolean healthy;
    private String statusMessage;

    public HealthCheckResult(String serviceName, boolean healthy, String statusMessage) {
        this.timestamp = LocalDateTime.now();
        this.serviceName = serviceName;
        this.healthy = healthy;
        this.statusMessage = statusMessage;
    }

    public boolean isHealthy() { return healthy; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getServiceName() { return serviceName; }
    public String getStatusMessage() { return statusMessage; }
}

class ResourceSnapshot {
    private LocalDateTime timestamp;
    private double appMemoryMB;
    private double dbMemoryMB;
    private double appCpuPercent;
    private double dbCpuPercent;

    public ResourceSnapshot() {
        this.timestamp = LocalDateTime.now();
    }

    public double getTotalMemoryMB() {
        return appMemoryMB + dbMemoryMB;
    }

    public double getAverageCpuPercent() {
        return (appCpuPercent + dbCpuPercent) / 2.0;
    }

    // Getters and setters
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getAppMemoryMB() { return appMemoryMB; }
    public void setAppMemoryMB(double appMemoryMB) { this.appMemoryMB = appMemoryMB; }
    public double getDbMemoryMB() { return dbMemoryMB; }
    public void setDbMemoryMB(double dbMemoryMB) { this.dbMemoryMB = dbMemoryMB; }
    public double getAppCpuPercent() { return appCpuPercent; }
    public void setAppCpuPercent(double appCpuPercent) { this.appCpuPercent = appCpuPercent; }
    public double getDbCpuPercent() { return dbCpuPercent; }
    public void setDbCpuPercent(double dbCpuPercent) { this.dbCpuPercent = dbCpuPercent; }
}
