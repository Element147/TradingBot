package com.algotrader.bot.repair;

import com.algotrader.bot.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RepairEngine {
    private static final Logger logger = LoggerFactory.getLogger(RepairEngine.class);
    private static final int MAX_REPAIR_ATTEMPTS = 3;
    private final BuildRepairActions buildRepairActions;
    private final OrchestrationRepairActions orchestrationRepairActions;
    private final HealthCheckRepairActions healthCheckRepairActions;

    public RepairEngine() {
        this(new BuildRepairActions(), new OrchestrationRepairActions(), new HealthCheckRepairActions());
    }

    RepairEngine(
        BuildRepairActions buildRepairActions,
        OrchestrationRepairActions orchestrationRepairActions,
        HealthCheckRepairActions healthCheckRepairActions
    ) {
        this.buildRepairActions = buildRepairActions;
        this.orchestrationRepairActions = orchestrationRepairActions;
        this.healthCheckRepairActions = healthCheckRepairActions;
    }

    public RepairResult repairBuildFailure(ValidationResult failure) {
        logger.info("Attempting to repair build failure");
        RepairAction action = selectRepairAction(failure);
        return executeRepairAction(action, failure);
    }

    public RepairResult repairOrchestrationFailure(ValidationResult failure) {
        logger.info("Attempting to repair orchestration failure");
        RepairAction action = RepairAction.RESTART_SERVICES;
        return executeRepairAction(action, failure);
    }

    public RepairResult repairHealthCheckFailure(ValidationResult failure) {
        logger.info("Attempting to repair health check failure");
        RepairAction action = RepairAction.RESTART_CONTAINER;
        return executeRepairAction(action, failure);
    }

    public RepairResult repairApiFailure(ValidationResult failure) {
        logger.info("Attempting to repair API failure");
        RepairAction action = RepairAction.RESTART_CONTAINER;
        return executeRepairAction(action, failure);
    }

    public RepairAction selectRepairAction(ValidationResult failure) {
        String reqId = failure.getRequirementId();
        String searchText = (failure.getRequirementName() + " " + failure.getMessage()).toLowerCase();
        
        if (reqId.startsWith("REQ-7")) {
            // Build failures
            if (reqId.contains("7.1") || reqId.contains("7.2")) {
                return RepairAction.CLEAN_GRADLE_CACHE;
            } else if (reqId.contains("7.5") || reqId.contains("7.6")) {
                return RepairAction.PRUNE_DOCKER_IMAGES;
            }
        } else if (reqId.startsWith("REQ-8")) {
            // Orchestration failures
            if (searchText.contains("port")) {
                return RepairAction.RESOLVE_PORT_CONFLICTS;
            }
            if (searchText.contains("orphan") || searchText.contains("network")) {
                return RepairAction.CLEANUP_ORPHANED_CONTAINERS;
            }
            return RepairAction.RESTART_SERVICES;
        } else if (reqId.startsWith("REQ-9")) {
            // API failures
            return RepairAction.RESTART_CONTAINER;
        }
        
        return RepairAction.CHECK_LOGS;
    }

    public RepairResult executeRepairAction(RepairAction action, ValidationResult failure) {
        logger.info("Executing repair action: {}", action);
        LocalDateTime start = LocalDateTime.now();
        
        try {
            boolean success = false;
            String message = "";
            
            switch (action) {
                case CLEAN_GRADLE_CACHE:
                    RepairResult cacheResult = buildRepairActions.cleanGradleCache();
                    success = cacheResult.isSuccessful();
                    message = cacheResult.getMessage();
                    break;
                case REBUILD_JAR:
                    RepairResult jarResult = buildRepairActions.rebuildJar();
                    success = jarResult.isSuccessful();
                    message = jarResult.getMessage();
                    break;
                case PRUNE_DOCKER_IMAGES:
                    RepairResult pruneResult = buildRepairActions.pruneDockerImages();
                    success = pruneResult.isSuccessful();
                    message = pruneResult.getMessage();
                    break;
                case REBUILD_DOCKER_IMAGE:
                    RepairResult imageResult = buildRepairActions.rebuildDockerImage();
                    success = imageResult.isSuccessful();
                    message = imageResult.getMessage();
                    break;
                case RESTART_SERVICES:
                    RepairResult stopResult = orchestrationRepairActions.stopAllServices();
                    if (!stopResult.isSuccessful()) {
                        success = false;
                        message = stopResult.getMessage();
                        break;
                    }
                    RepairResult startResult = orchestrationRepairActions.startAllServices();
                    success = startResult.isSuccessful();
                    message = startResult.getMessage();
                    break;
                case RESOLVE_PORT_CONFLICTS:
                    RepairResult portResult = orchestrationRepairActions.resolvePortConflicts();
                    success = portResult.isSuccessful();
                    message = portResult.getMessage();
                    break;
                case CLEANUP_ORPHANED_CONTAINERS:
                    RepairResult cleanupResult = orchestrationRepairActions.cleanupOrphanedContainers();
                    if (!cleanupResult.isSuccessful()) {
                        success = false;
                        message = cleanupResult.getMessage();
                        break;
                    }
                    RepairResult networkResult = orchestrationRepairActions.checkNetworkConflicts();
                    success = networkResult.isSuccessful();
                    message = cleanupResult.getMessage() + "; " + networkResult.getMessage();
                    break;
                case RESTART_CONTAINER:
                    RepairResult restartResult = healthCheckRepairActions.restartContainer(resolveServiceName(failure));
                    success = restartResult.isSuccessful();
                    message = restartResult.getMessage();
                    break;
                case CHECK_LOGS:
                    RepairResult logResult = healthCheckRepairActions.checkServiceLogs(resolveServiceName(failure));
                    success = logResult.isSuccessful();
                    message = logResult.getMessage();
                    break;
                default:
                    success = false;
                    message = "Unknown repair action";
            }
            
            RepairResult result = new RepairResult(success, message);
            logger.info("Repair action {} completed: {}", action, message);
            return result;
            
        } catch (Exception e) {
            logger.error("Error executing repair action", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public ValidationResult retryValidation(ValidationResult originalFailure) {
        logger.info("Retrying validation after repair");
        // This would trigger the original validation again
        return originalFailure;
    }

    public boolean shouldRetry(int attemptCount) {
        return attemptCount < MAX_REPAIR_ATTEMPTS;
    }

    public FailureReport generateFailureReport(List<RepairAttempt> attempts) {
        logger.info("Generating failure report for {} attempts", attempts.size());
        
        if (attempts.isEmpty()) {
            return null;
        }
        
        FailureReport report = new FailureReport(attempts.get(0).getTriggeringFailure());
        report.setEnvironment("Docker Production");
        
        for (RepairAttempt attempt : attempts) {
            report.addRepairAttempt(attempt);
            if (attempt.getLogOutput() != null) {
                report.addDiagnosticLog(attempt.getLogOutput());
            }
        }
        
        report.addSystemInfo("maxAttempts", String.valueOf(MAX_REPAIR_ATTEMPTS));
        report.addSystemInfo("totalAttempts", String.valueOf(attempts.size()));
        
        return report;
    }

    private String resolveServiceName(ValidationResult failure) {
        String searchText = (failure.getRequirementName() + " " + failure.getMessage()).toLowerCase();
        if (searchText.contains("postgres")) {
            return "postgres";
        }
        return "algotrading-app";
    }
}
