package com.algotrader.bot.validation;

import java.time.LocalDateTime;

public class ResourceMetrics {
    private ContainerMetrics applicationMetrics;
    private ContainerMetrics databaseMetrics;
    private long totalMemoryUsageMB;
    private long totalDiskUsageGB;
    private LocalDateTime collectedAt;

    public ResourceMetrics() {
        this.collectedAt = LocalDateTime.now();
        this.applicationMetrics = new ContainerMetrics("algotrading-app");
        this.databaseMetrics = new ContainerMetrics("algotrading-postgres");
    }

    public boolean isWithinLimits() {
        return applicationMetrics.getMemoryUsageMB() < 512
            && databaseMetrics.getMemoryUsageMB() < 256
            && totalMemoryUsageMB < 768;
    }

    // Getters and setters
    public ContainerMetrics getApplicationMetrics() { return applicationMetrics; }
    public void setApplicationMetrics(ContainerMetrics applicationMetrics) { 
        this.applicationMetrics = applicationMetrics; 
    }
    
    public ContainerMetrics getDatabaseMetrics() { return databaseMetrics; }
    public void setDatabaseMetrics(ContainerMetrics databaseMetrics) { 
        this.databaseMetrics = databaseMetrics; 
    }
    
    public long getTotalMemoryUsageMB() { return totalMemoryUsageMB; }
    public void setTotalMemoryUsageMB(long totalMemoryUsageMB) { 
        this.totalMemoryUsageMB = totalMemoryUsageMB; 
    }
    
    public long getTotalDiskUsageGB() { return totalDiskUsageGB; }
    public void setTotalDiskUsageGB(long totalDiskUsageGB) { 
        this.totalDiskUsageGB = totalDiskUsageGB; 
    }
    
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { 
        this.collectedAt = collectedAt; 
    }
}
