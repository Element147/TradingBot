package com.algotrader.bot.validation;

public class ContainerMetrics {
    private String containerName;
    private long memoryUsageMB;
    private double cpuUsagePercent;
    private long diskUsageMB;
    private int restartCount;

    public ContainerMetrics(String containerName) {
        this.containerName = containerName;
    }

    public boolean isHealthy() {
        return restartCount == 0;
    }

    // Getters and setters
    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    
    public long getMemoryUsageMB() { return memoryUsageMB; }
    public void setMemoryUsageMB(long memoryUsageMB) { this.memoryUsageMB = memoryUsageMB; }
    
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public void setCpuUsagePercent(double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; }
    
    public long getDiskUsageMB() { return diskUsageMB; }
    public void setDiskUsageMB(long diskUsageMB) { this.diskUsageMB = diskUsageMB; }
    
    public int getRestartCount() { return restartCount; }
    public void setRestartCount(int restartCount) { this.restartCount = restartCount; }
}
