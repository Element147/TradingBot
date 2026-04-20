package com.algotrader.bot.validation;

import com.algotrader.bot.repair.RepairWorkspaceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class OrchestrationValidator {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationValidator.class);
    private final RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

    public ValidationResult validateServiceStartup() {
        logger.info("Starting service orchestration validation");
        LocalDateTime start = LocalDateTime.now();
        
        ValidationResult startResult = startServices();
        if (startResult.isFailed()) {
            return startResult;
        }
        
        ValidationResult postgresResult = validateServiceHealth("postgres", Duration.ofSeconds(60));
        if (postgresResult.isFailed()) {
            return postgresResult;
        }
        
        ValidationResult appResult = validateServiceHealth("algotrading-app", Duration.ofSeconds(120));
        if (appResult.isFailed()) {
            return appResult;
        }
        
        ValidationResult containersResult = validateAllContainersRunning();
        if (containersResult.isFailed()) {
            return containersResult;
        }
        
        ValidationResult dbConnResult = validateServiceLogs("algotrading-app", 
            List.of("HikariPool", "connection", "database"));
        if (dbConnResult.isFailed()) {
            return dbConnResult;
        }
        
        ValidationResult result = new ValidationResult(
            "REQ-8", 
            "Service Orchestration", 
            ValidationStatus.PASSED, 
            "All services started and healthy"
        );
        result.setExecutionTime(Duration.between(start, LocalDateTime.now()));
        logger.info("Service orchestration validation completed successfully");
        return result;
    }

    public ValidationResult startServices() {
        logger.info("Starting Docker Compose services");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                workspaceSupport.composeProjectName(),
                "-f",
                workspaceSupport.composeFile().toString(),
                "up",
                "-d"
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Docker Compose: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Services started successfully");
                return new ValidationResult(
                    "REQ-8.1", 
                    "Start Services", 
                    ValidationStatus.PASSED, 
                    "Services started"
                );
            } else {
                logger.error("Failed to start services, exit code: {}", exitCode);
                ValidationResult result = new ValidationResult(
                    "REQ-8.1", 
                    "Start Services", 
                    ValidationStatus.FAILED, 
                    "Failed to start services: exit code " + exitCode
                );
                result.addMetadata("output", output.toString());
                return result;
            }
        } catch (Exception e) {
            logger.error("Error starting services", e);
            return new ValidationResult(
                "REQ-8.1", 
                "Start Services", 
                ValidationStatus.FAILED, 
                "Error starting services: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateServiceHealth(String serviceName, Duration timeout) {
        logger.info("Validating health of service: {} (timeout: {}s)", serviceName, timeout.getSeconds());
        LocalDateTime start = LocalDateTime.now();
        int attempt = 0;
        
        while (Duration.between(start, LocalDateTime.now()).compareTo(timeout) < 0) {
            attempt++;
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
                StringBuilder output = new StringBuilder();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }
                
                int exitCode = process.waitFor();
                String healthStatus = output.toString().trim();
                
                logger.debug("Service {} health check attempt {}: {}", serviceName, attempt, healthStatus);
                
                if (exitCode == 0 && "healthy".equals(healthStatus)) {
                    logger.info("Service {} is healthy after {} attempts", serviceName, attempt);
                    ValidationResult result = new ValidationResult(
                        "REQ-8.2", 
                        "Service Health: " + serviceName, 
                        ValidationStatus.PASSED, 
                        serviceName + " is healthy"
                    );
                    result.addMetadata("attempts", attempt);
                    result.addMetadata("duration", Duration.between(start, LocalDateTime.now()).getSeconds());
                    return result;
                }
                
                // Exponential backoff
                Thread.sleep(Math.min(1000 * (long)Math.pow(2, Math.min(attempt - 1, 5)), 10000));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Health check interrupted", e);
                return new ValidationResult(
                    "REQ-8.2", 
                    "Service Health: " + serviceName, 
                    ValidationStatus.FAILED, 
                    "Health check interrupted"
                );
            } catch (Exception e) {
                logger.error("Error checking service health", e);
            }
        }
        
        logger.error("Service {} did not become healthy within timeout", serviceName);
        return new ValidationResult(
            "REQ-8.2", 
            "Service Health: " + serviceName, 
            ValidationStatus.FAILED, 
            serviceName + " did not become healthy within " + timeout.getSeconds() + "s"
        );
    }

    public ValidationResult validateAllContainersRunning() {
        logger.info("Validating all containers are running");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "ps",
                "--filter",
                "name=algotrading",
                "--format",
                "{{.Names}}"
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                logger.error("Failed to list containers");
                return new ValidationResult(
                    "REQ-8.8", 
                    "Containers Running", 
                    ValidationStatus.FAILED, 
                    "Failed to list containers"
                );
            }
            
            String[] containers = output.toString().trim().split("\n");
            boolean hasPostgres = false;
            boolean hasApp = false;
            
            for (String container : containers) {
                if (container.contains("postgres")) hasPostgres = true;
                if (container.contains("app")) hasApp = true;
            }
            
            if (hasPostgres && hasApp) {
                logger.info("All required containers are running");
                return new ValidationResult(
                    "REQ-8.8", 
                    "Containers Running", 
                    ValidationStatus.PASSED, 
                    "All containers running"
                );
            } else {
                logger.error("Not all containers running. Postgres: {}, App: {}", hasPostgres, hasApp);
                return new ValidationResult(
                    "REQ-8.8", 
                    "Containers Running", 
                    ValidationStatus.FAILED, 
                    String.format("Missing containers - Postgres: %s, App: %s", hasPostgres, hasApp)
                );
            }
        } catch (Exception e) {
            logger.error("Error validating containers", e);
            return new ValidationResult(
                "REQ-8.8", 
                "Containers Running", 
                ValidationStatus.FAILED, 
                "Error validating containers: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateServiceLogs(String serviceName, List<String> expectedMessages) {
        logger.info("Validating logs for service: {}", serviceName);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                workspaceSupport.composeProjectName(),
                "-f",
                workspaceSupport.composeFile().toString(),
                "logs",
                workspaceSupport.composeServiceFor(serviceName)
            );
            pb.directory(workspaceSupport.repoRoot().toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                logger.error("Failed to get logs for service: {}", serviceName);
                return new ValidationResult(
                    "REQ-8.7", 
                    "Service Logs: " + serviceName, 
                    ValidationStatus.FAILED, 
                    "Failed to get logs"
                );
            }
            
            String logs = output.toString().toLowerCase();
            boolean allFound = true;
            StringBuilder missing = new StringBuilder();
            
            for (String expected : expectedMessages) {
                if (!logs.contains(expected.toLowerCase())) {
                    allFound = false;
                    missing.append(expected).append(", ");
                }
            }
            
            if (allFound) {
                logger.info("All expected log messages found for service: {}", serviceName);
                return new ValidationResult(
                    "REQ-8.7", 
                    "Service Logs: " + serviceName, 
                    ValidationStatus.PASSED, 
                    "Expected log messages found"
                );
            } else {
                logger.error("Missing log messages for service {}: {}", serviceName, missing);
                return new ValidationResult(
                    "REQ-8.7", 
                    "Service Logs: " + serviceName, 
                    ValidationStatus.FAILED, 
                    "Missing log messages: " + missing
                );
            }
        } catch (Exception e) {
            logger.error("Error validating service logs", e);
            return new ValidationResult(
                "REQ-8.7", 
                "Service Logs: " + serviceName, 
                ValidationStatus.FAILED, 
                "Error validating logs: " + e.getMessage()
            );
        }
    }
}
