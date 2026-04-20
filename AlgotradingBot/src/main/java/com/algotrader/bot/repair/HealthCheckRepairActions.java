package com.algotrader.bot.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;

public class HealthCheckRepairActions {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckRepairActions.class);
    private final RepairWorkspaceSupport workspaceSupport;

    public HealthCheckRepairActions() {
        this(RepairWorkspaceSupport.detect());
    }

    HealthCheckRepairActions(RepairWorkspaceSupport workspaceSupport) {
        this.workspaceSupport = workspaceSupport;
    }

    public RepairResult restartContainer(String serviceName) {
        logger.info("Restarting container: {}", serviceName);
        try {
            String composeService = workspaceSupport.composeServiceFor(serviceName);
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                workspaceSupport.composeProjectName(),
                "-f",
                workspaceSupport.composeFile().toString(),
                "restart",
                composeService
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("Docker: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Container {} restarted", serviceName);
                return new RepairResult(true, "Container restarted: " + serviceName);
            } else {
                logger.error("Failed to restart container {}", serviceName);
                return new RepairResult(false, "Restart failed");
            }
        } catch (Exception e) {
            logger.error("Error restarting container", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult checkServiceLogs(String serviceName) {
        logger.info("Checking logs for service: {}", serviceName);
        try {
            String composeService = workspaceSupport.composeServiceFor(serviceName);
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
                composeService
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder logs = new StringBuilder();
            StringBuilder errors = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append("\n");
                    if (line.contains("ERROR") || line.contains("FATAL") || line.contains("Exception")) {
                        errors.append(line).append("\n");
                    }
                }
            }
            
            process.waitFor();
            
            if (errors.length() > 0) {
                logger.warn("Errors found in logs for {}: {}", serviceName, errors);
                RepairResult result = new RepairResult(true, "Errors found in logs");
                return result;
            } else {
                logger.info("No errors found in logs for {}", serviceName);
                return new RepairResult(true, "No errors in logs");
            }
        } catch (Exception e) {
            logger.error("Error checking logs", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult waitForHealthy(String serviceName, Duration timeout) {
        logger.info("Waiting for {} to become healthy (timeout: {}s)", serviceName, timeout.getSeconds());
        LocalDateTime start = LocalDateTime.now();
        
        while (Duration.between(start, LocalDateTime.now()).compareTo(timeout) < 0) {
            try {
                String containerName = workspaceSupport.containerNameFor(serviceName);
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
                
                if ("healthy".equals(status)) {
                    logger.info("Service {} is healthy", serviceName);
                    return new RepairResult(true, serviceName + " is healthy");
                }
                
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new RepairResult(false, "Wait interrupted");
            } catch (Exception e) {
                logger.error("Error checking health", e);
            }
        }
        
        logger.error("Service {} did not become healthy within timeout", serviceName);
        return new RepairResult(false, serviceName + " not healthy within timeout");
    }

    public RepairResult diagnosePostgresFailure() {
        logger.info("Diagnosing PostgreSQL failure");
        RepairResult logResult = checkServiceLogs("postgres");
        return new RepairResult(true, "Postgres diagnosis: " + logResult.getMessage());
    }

    public RepairResult diagnoseApplicationFailure() {
        logger.info("Diagnosing application failure");
        RepairResult logResult = checkServiceLogs("algotrading-app");
        return new RepairResult(true, "Application diagnosis: " + logResult.getMessage());
    }
}
