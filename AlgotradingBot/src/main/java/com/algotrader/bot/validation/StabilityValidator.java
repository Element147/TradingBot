package com.algotrader.bot.validation;

import com.algotrader.bot.repair.RepairWorkspaceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;

public class StabilityValidator {
    private static final Logger logger = LoggerFactory.getLogger(StabilityValidator.class);
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private final RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

    public ValidationResult runStabilityTest(Duration duration) {
        logger.info("Starting stability test for {} minutes", duration.toMinutes());
        LocalDateTime start = LocalDateTime.now();
        StabilityMetrics metrics = new StabilityMetrics();
        metrics.setTestDuration(duration);
        
        long endTime = System.currentTimeMillis() + duration.toMillis();
        int checkCount = 0;
        
        while (System.currentTimeMillis() < endTime) {
            checkCount++;
            long remainingMinutes = (endTime - System.currentTimeMillis()) / 60000;
            logger.info("Stability check #{} - {} minutes remaining", checkCount, remainingMinutes);
            
            // Health checks
            performHealthChecks(metrics);
            
            // Resource monitoring
            collectResourceMetrics(metrics);
            
            // Check for restarts
            checkContainerRestarts(metrics);
            
            // Scan for errors
            scanErrorLogs(metrics);
            
            // Check connections
            checkDatabaseConnection(metrics);
            // Wait for next check interval or end
            long sleepTime = Math.min(CHECK_INTERVAL_MS, endTime - System.currentTimeMillis());
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Stability test interrupted", e);
                    return new ValidationResult(
                        "REQ-10", 
                        "Stability Test", 
                        ValidationStatus.FAILED, 
                        "Test interrupted"
                    );
                }
            }
        }
        
        logger.info("Stability test completed after {} checks", checkCount);
        return generateStabilityReport(metrics);
    }

    private void performHealthChecks(StabilityMetrics metrics) {
        String[] services = {"postgres", "algotrading-app"};
        for (String service : services) {
            try {
                String containerName = workspaceSupport.containerNameFor(service);
                ProcessBuilder pb = new ProcessBuilder(
                    "docker",
                    "inspect",
                    "--format",
                    "{{.State.Health.Status}}",
                    containerName
                );
                pb.directory(workspaceSupport.repoRoot().toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String status = reader.readLine();
                reader.close();
                process.waitFor();
                
                boolean healthy = "healthy".equals(status);
                metrics.addHealthCheck(new HealthCheckResult(service, healthy, status));
                logger.debug("Service {} health: {}", service, status);
            } catch (Exception e) {
                logger.error("Error checking health for service: {}", service, e);
                metrics.addHealthCheck(new HealthCheckResult(service, false, "error"));
            }
        }
    }

    private void collectResourceMetrics(StabilityMetrics metrics) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "stats",
                "--no-stream",
                "--format",
                "{{.Name}},{{.MemUsage}},{{.CPUPerc}}"
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            ResourceSnapshot snapshot = new ResourceSnapshot();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String name = parts[0];
                        String memUsage = parts[1].split("/")[0].trim();
                        String cpuPerc = parts[2].replace("%", "").trim();
                        
                        double memMB = parseMemoryToMB(memUsage);
                        double cpu = Double.parseDouble(cpuPerc);
                        
                        if (name.contains("app")) {
                            snapshot.setAppMemoryMB(memMB);
                            snapshot.setAppCpuPercent(cpu);
                        } else if (name.contains("postgres")) {
                            snapshot.setDbMemoryMB(memMB);
                            snapshot.setDbCpuPercent(cpu);
                        }
                    }
                }
            }
            
            process.waitFor();
            metrics.addResourceSnapshot(snapshot);
            logger.debug("Resource snapshot collected - App: {} MB, DB: {} MB",
                snapshot.getAppMemoryMB(), snapshot.getDbMemoryMB());
        } catch (Exception e) {
            logger.error("Error collecting resource metrics", e);
        }
    }

    private void checkContainerRestarts(StabilityMetrics metrics) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "ps",
                "--filter",
                "name=algotrading",
                "--format",
                "{{.Names}},{{.Status}}"
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int restarts = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("restarting")) {
                        restarts++;
                    }
                }
            }
            
            process.waitFor();
            metrics.setContainerRestarts(restarts);
            logger.debug("Container restarts detected: {}", restarts);
        } catch (Exception e) {
            logger.error("Error checking container restarts", e);
        }
    }

    private void scanErrorLogs(StabilityMetrics metrics) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                workspaceSupport.composeProjectName(),
                "-f",
                workspaceSupport.composeFile().toString(),
                "logs",
                workspaceSupport.composeServiceFor("algotrading-app")
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int errorCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("ERROR") || line.contains("FATAL")) {
                        errorCount++;
                    }
                }
            }
            
            process.waitFor();
            metrics.setErrorLogCount(errorCount);
            logger.debug("Error logs found: {}", errorCount);
        } catch (Exception e) {
            logger.error("Error scanning logs", e);
        }
    }

    private void checkDatabaseConnection(StabilityMetrics metrics) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                workspaceSupport.composeProjectName(),
                "-f",
                workspaceSupport.composeFile().toString(),
                "logs",
                "--tail",
                "100",
                workspaceSupport.composeServiceFor("algotrading-app")
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean connectionIssue = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("database") && 
                        (line.toLowerCase().contains("error") || line.toLowerCase().contains("failed"))) {
                        connectionIssue = true;
                        break;
                    }
                }
            }
            
            process.waitFor();
            metrics.setDatabaseConnectionStable(!connectionIssue);
            logger.debug("Database connection stable: {}", !connectionIssue);
        } catch (Exception e) {
            logger.error("Error checking database connection", e);
            metrics.setDatabaseConnectionStable(false);
        }
    }

    public ValidationResult generateStabilityReport(StabilityMetrics metrics) {
        logger.info("Generating stability report");
        
        StringBuilder report = new StringBuilder();
        report.append("=== STABILITY TEST REPORT ===\n");
        report.append("Duration: ").append(metrics.getTestDuration().toMinutes()).append(" minutes\n");
        report.append("Health Checks: ").append(metrics.getHealthChecks().size()).append("\n");
        report.append("Container Restarts: ").append(metrics.getContainerRestarts()).append("\n");
        report.append("Error Logs: ").append(metrics.getErrorLogCount()).append("\n");
        report.append("Average Memory: ").append(String.format("%.2f", metrics.getAverageMemoryUsageMB())).append(" MB\n");
        report.append("Average CPU: ").append(String.format("%.2f", metrics.getAverageCpuUsagePercent())).append("%\n");
        report.append("Database Connection Stable: ").append(metrics.isDatabaseConnectionStable()).append("\n");
        
        if (metrics.isStable()) {
            logger.info("Stability test PASSED");
            ValidationResult result = new ValidationResult(
                "REQ-10", 
                "Stability Test", 
                ValidationStatus.PASSED, 
                "System stable for " + metrics.getTestDuration().toMinutes() + " minutes"
            );
            result.addMetadata("report", report.toString());
            result.addMetadata("metrics", metrics);
            return result;
        } else {
            logger.error("Stability test FAILED");
            ValidationResult result = new ValidationResult(
                "REQ-10", 
                "Stability Test", 
                ValidationStatus.FAILED, 
                "System not stable - restarts: " + metrics.getContainerRestarts() + 
                ", errors: " + metrics.getErrorLogCount()
            );
            result.addMetadata("report", report.toString());
            result.addMetadata("metrics", metrics);
            return result;
        }
    }

    private double parseMemoryToMB(String memStr) {
        memStr = memStr.toUpperCase().trim();
        if (memStr.endsWith("GIB") || memStr.endsWith("GB")) {
            double gb = Double.parseDouble(memStr.replaceAll("[^0-9.]", ""));
            return gb * 1024;
        } else if (memStr.endsWith("MIB") || memStr.endsWith("MB")) {
            return Double.parseDouble(memStr.replaceAll("[^0-9.]", ""));
        } else if (memStr.endsWith("KIB") || memStr.endsWith("KB")) {
            double kb = Double.parseDouble(memStr.replaceAll("[^0-9.]", ""));
            return kb / 1024;
        }
        return 0;
    }
}
