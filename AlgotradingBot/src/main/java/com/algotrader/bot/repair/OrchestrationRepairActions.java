package com.algotrader.bot.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class OrchestrationRepairActions {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationRepairActions.class);
    private final RepairWorkspaceSupport workspaceSupport;

    public OrchestrationRepairActions() {
        this(RepairWorkspaceSupport.detect());
    }

    OrchestrationRepairActions(RepairWorkspaceSupport workspaceSupport) {
        this.workspaceSupport = workspaceSupport;
    }

    public RepairResult stopAllServices() {
        logger.info("Stopping all services");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                workspaceSupport.scriptPath("stop.ps1").toString()
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
                logger.info("All services stopped");
                return new RepairResult(true, "Services stopped");
            } else {
                logger.error("Failed to stop services");
                return new RepairResult(false, "Stop failed");
            }
        } catch (Exception e) {
            logger.error("Error stopping services", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult startAllServices() {
        logger.info("Starting all services");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                workspaceSupport.scriptPath("run.ps1").toString()
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
                logger.info("All services started");
                return new RepairResult(true, "Services started");
            } else {
                logger.error("Failed to start services");
                return new RepairResult(false, "Start failed");
            }
        } catch (Exception e) {
            logger.error("Error starting services", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult checkPortConflicts() {
        logger.info("Checking for port conflicts");
        Map<Integer, String> conflicts = workspaceSupport.findListeningProcesses(workspaceSupport.managedPorts());

        if (conflicts.isEmpty()) {
            logger.info("No port conflicts detected");
            return new RepairResult(true, "No port conflicts");
        }

        logger.warn("Port conflicts detected: {}", conflicts);
        return new RepairResult(false, "Port conflicts detected: " + conflicts);
    }

    public RepairResult resolvePortConflicts() {
        logger.info("Attempting to resolve port conflicts");
        Map<Integer, String> conflicts = workspaceSupport.findListeningProcesses(workspaceSupport.managedPorts());
        if (conflicts.isEmpty()) {
            return new RepairResult(true, "No port conflicts detected");
        }

        RepairResult stopResult = stopAllServices();
        if (!stopResult.isSuccessful()) {
            return new RepairResult(false, "Unable to resolve ports because stop.ps1 failed: " + stopResult.getMessage());
        }

        try {
            stopPidFromFile("backend");
            stopPidFromFile("frontend");
        } catch (Exception ex) {
            logger.warn("Unable to stop PID file processes during port resolution: {}", ex.getMessage());
        }

        Map<Integer, String> remainingConflicts = workspaceSupport.findListeningProcesses(workspaceSupport.managedPorts());
        if (remainingConflicts.isEmpty()) {
            return new RepairResult(true, "Port conflicts resolved via stop.ps1 and managed PID cleanup");
        }

        return new RepairResult(false, "Port conflicts remain after automated cleanup: " + remainingConflicts);
    }

    public RepairResult checkNetworkConflicts() {
        logger.info("Checking for network conflicts");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "network",
                "inspect",
                workspaceSupport.managedNetworkName()
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
            
            if (exitCode == 0) {
                logger.info("Network check completed");
                return new RepairResult(true, "Docker network is present and inspectable");
            }

            if (output.toString().contains("No such network")) {
                return new RepairResult(true, "No managed Docker network is currently allocated");
            }

            logger.error("Network check failed");
            return new RepairResult(false, "Network check failed: " + output.toString().trim());
        } catch (Exception e) {
            logger.error("Error checking networks", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult cleanupOrphanedContainers() {
        logger.info("Cleaning up orphaned containers");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                workspaceSupport.composeProjectName(),
                "-f",
                workspaceSupport.composeFile().toString(),
                "down",
                "--remove-orphans"
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
                logger.info("Orphaned containers cleaned");
                return new RepairResult(true, "Containers cleaned");
            } else {
                logger.error("Failed to clean containers");
                return new RepairResult(false, "Cleanup failed");
            }
        } catch (Exception e) {
            logger.error("Error cleaning containers", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    private void stopPidFromFile(String name) throws Exception {
        Path pidFile = workspaceSupport.pidFile(name);
        if (!Files.exists(pidFile)) {
            return;
        }
        workspaceSupport.stopPidIfPresent(pidFile);
    }
}
