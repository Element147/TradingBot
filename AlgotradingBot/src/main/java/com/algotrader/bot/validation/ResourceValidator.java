package com.algotrader.bot.validation;

import com.algotrader.bot.repair.RepairWorkspaceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ResourceValidator {
    private static final Logger logger = LoggerFactory.getLogger(ResourceValidator.class);
    private static final long MAX_APP_MEMORY_MB = 512;
    private static final long MAX_DB_MEMORY_MB = 256;
    private static final long MAX_TOTAL_MEMORY_MB = 768;
    private static final long MAX_DISK_USAGE_GB = 2;
    private final RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

    public ValidationResult validateMemoryUsage() {
        logger.info("Validating memory usage");
        ResourceMetrics metrics = collectResourceMetrics();
        
        if (metrics == null) {
            return new ValidationResult(
                "REQ-11", 
                "Memory Usage", 
                ValidationStatus.FAILED, 
                "Failed to collect resource metrics"
            );
        }
        
        StringBuilder issues = new StringBuilder();
        boolean passed = true;
        
        if (metrics.getApplicationMetrics().getMemoryUsageMB() > MAX_APP_MEMORY_MB) {
            issues.append("App memory ").append(metrics.getApplicationMetrics().getMemoryUsageMB())
                  .append("MB exceeds ").append(MAX_APP_MEMORY_MB).append("MB; ");
            passed = false;
        }
        
        if (metrics.getDatabaseMetrics().getMemoryUsageMB() > MAX_DB_MEMORY_MB) {
            issues.append("DB memory ").append(metrics.getDatabaseMetrics().getMemoryUsageMB())
                  .append("MB exceeds ").append(MAX_DB_MEMORY_MB).append("MB; ");
            passed = false;
        }
        
        if (metrics.getTotalMemoryUsageMB() > MAX_TOTAL_MEMORY_MB) {
            issues.append("Total memory ").append(metrics.getTotalMemoryUsageMB())
                  .append("MB exceeds ").append(MAX_TOTAL_MEMORY_MB).append("MB; ");
            passed = false;
        }
        
        if (passed) {
            logger.info("Memory usage validation passed");
            ValidationResult result = new ValidationResult(
                "REQ-11", 
                "Memory Usage", 
                ValidationStatus.PASSED, 
                String.format("Memory within limits - App: %dMB, DB: %dMB, Total: %dMB",
                    metrics.getApplicationMetrics().getMemoryUsageMB(),
                    metrics.getDatabaseMetrics().getMemoryUsageMB(),
                    metrics.getTotalMemoryUsageMB())
            );
            result.addMetadata("metrics", metrics);
            return result;
        } else {
            logger.error("Memory usage validation failed: {}", issues);
            ValidationResult result = new ValidationResult(
                "REQ-11", 
                "Memory Usage", 
                ValidationStatus.FAILED, 
                issues.toString()
            );
            result.addMetadata("metrics", metrics);
            return result;
        }
    }

    public ValidationResult validateDiskUsage() {
        logger.info("Validating disk usage");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "system", "df", "-v");
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            long totalSizeBytes = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean inVolumes = false;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Local Volumes")) {
                        inVolumes = true;
                        continue;
                    }
                    if (inVolumes && line.contains("postgres_data")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 3) {
                            String sizeStr = parts[2];
                            totalSizeBytes += parseSizeToBytes(sizeStr);
                        }
                    }
                }
            }
            
            process.waitFor();
            
            long totalSizeGB = totalSizeBytes / (1024 * 1024 * 1024);
            logger.info("Total disk usage: {} GB", totalSizeGB);
            
            if (totalSizeGB < MAX_DISK_USAGE_GB) {
                return new ValidationResult(
                    "REQ-11.8", 
                    "Disk Usage", 
                    ValidationStatus.PASSED, 
                    "Disk usage " + totalSizeGB + "GB within limit"
                );
            } else {
                return new ValidationResult(
                    "REQ-11.8", 
                    "Disk Usage", 
                    ValidationStatus.FAILED, 
                    "Disk usage " + totalSizeGB + "GB exceeds " + MAX_DISK_USAGE_GB + "GB"
                );
            }
        } catch (Exception e) {
            logger.error("Error validating disk usage", e);
            return new ValidationResult(
                "REQ-11.8", 
                "Disk Usage", 
                ValidationStatus.FAILED, 
                "Error validating disk usage: " + e.getMessage()
            );
        }
    }

    public ResourceMetrics collectResourceMetrics() {
        logger.info("Collecting resource metrics");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stats", "--no-stream", 
                "--format", "{{.Name}},{{.MemUsage}},{{.CPUPerc}}");
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            ResourceMetrics metrics = new ResourceMetrics();
            long totalMemory = 0;
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String name = parts[0];
                        String memUsage = parts[1].split("/")[0].trim();
                        String cpuPerc = parts[2].replace("%", "").trim();
                        
                        long memMB = parseMemoryToMB(memUsage);
                        double cpu = Double.parseDouble(cpuPerc);
                        
                        if (name.contains("app")) {
                            metrics.getApplicationMetrics().setMemoryUsageMB(memMB);
                            metrics.getApplicationMetrics().setCpuUsagePercent(cpu);
                        } else if (name.contains("postgres")) {
                            metrics.getDatabaseMetrics().setMemoryUsageMB(memMB);
                            metrics.getDatabaseMetrics().setCpuUsagePercent(cpu);
                        }
                        
                        totalMemory += memMB;
                    }
                }
            }
            
            process.waitFor();
            metrics.setTotalMemoryUsageMB(totalMemory);
            
            logger.info("Resource metrics collected - Total memory: {} MB", totalMemory);
            return metrics;
        } catch (Exception e) {
            logger.error("Error collecting resource metrics", e);
            return null;
        }
    }

    public String generateResourceReport(ResourceMetrics metrics) {
        StringBuilder report = new StringBuilder();
        report.append("=== RESOURCE USAGE REPORT ===\n");
        report.append("Application Memory: ").append(metrics.getApplicationMetrics().getMemoryUsageMB()).append(" MB\n");
        report.append("Database Memory: ").append(metrics.getDatabaseMetrics().getMemoryUsageMB()).append(" MB\n");
        report.append("Total Memory: ").append(metrics.getTotalMemoryUsageMB()).append(" MB\n");
        report.append("Total Disk: ").append(metrics.getTotalDiskUsageGB()).append(" GB\n");
        return report.toString();
    }

    private long parseMemoryToMB(String memStr) {
        memStr = memStr.toUpperCase().trim();
        if (memStr.endsWith("GIB") || memStr.endsWith("GB")) {
            double gb = Double.parseDouble(memStr.replaceAll("[^0-9.]", ""));
            return (long) (gb * 1024);
        } else if (memStr.endsWith("MIB") || memStr.endsWith("MB")) {
            return (long) Double.parseDouble(memStr.replaceAll("[^0-9.]", ""));
        } else if (memStr.endsWith("KIB") || memStr.endsWith("KB")) {
            double kb = Double.parseDouble(memStr.replaceAll("[^0-9.]", ""));
            return (long) (kb / 1024);
        }
        return 0;
    }

    private long parseSizeToBytes(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        if (sizeStr.endsWith("GB")) {
            double gb = Double.parseDouble(sizeStr.replace("GB", "").trim());
            return (long) (gb * 1024 * 1024 * 1024);
        } else if (sizeStr.endsWith("MB")) {
            double mb = Double.parseDouble(sizeStr.replace("MB", "").trim());
            return (long) (mb * 1024 * 1024);
        } else if (sizeStr.endsWith("KB")) {
            double kb = Double.parseDouble(sizeStr.replace("KB", "").trim());
            return (long) (kb * 1024);
        }
        return 0;
    }
}
