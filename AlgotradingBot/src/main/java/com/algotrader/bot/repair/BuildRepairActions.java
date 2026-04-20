package com.algotrader.bot.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BuildRepairActions {
    private static final Logger logger = LoggerFactory.getLogger(BuildRepairActions.class);
    private final RepairWorkspaceSupport workspaceSupport;

    public BuildRepairActions() {
        this(RepairWorkspaceSupport.detect());
    }

    BuildRepairActions(RepairWorkspaceSupport workspaceSupport) {
        this.workspaceSupport = workspaceSupport;
    }

    public RepairResult cleanGradleCache() {
        logger.info("Cleaning Gradle cache");
        try {
            Path cacheDir = Paths.get(System.getProperty("user.home"), ".gradle", "caches");
            if (Files.exists(cacheDir)) {
                deleteDirectory(cacheDir.toFile());
                logger.info("Gradle cache cleaned");
                return new RepairResult(true, "Gradle cache cleaned");
            } else {
                logger.info("Gradle cache directory not found");
                return new RepairResult(true, "No cache to clean");
            }
        } catch (Exception e) {
            logger.error("Error cleaning Gradle cache", e);
            return new RepairResult(false, "Error cleaning cache: " + e.getMessage());
        }
    }

    public RepairResult cleanBuildDirectory() {
        logger.info("Cleaning build directory");
        try {
            ProcessBuilder pb = new ProcessBuilder("gradlew.bat", "clean");
            pb.directory(workspaceSupport.backendDir().toFile());
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
                logger.info("Build directory cleaned");
                return new RepairResult(true, "Build directory cleaned");
            } else {
                logger.error("Failed to clean build directory");
                return new RepairResult(false, "Clean failed: " + output);
            }
        } catch (Exception e) {
            logger.error("Error cleaning build directory", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult rebuildJar() {
        logger.info("Rebuilding JAR");
        try {
            ProcessBuilder pb = new ProcessBuilder("gradlew.bat", "bootJar");
            pb.directory(workspaceSupport.backendDir().toFile());
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
                logger.info("JAR rebuilt successfully");
                return new RepairResult(true, "JAR rebuilt");
            } else {
                logger.error("Failed to rebuild JAR");
                return new RepairResult(false, "Rebuild failed: " + output);
            }
        } catch (Exception e) {
            logger.error("Error rebuilding JAR", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult checkDiskSpace() {
        logger.info("Checking disk space");
        try {
            File root = workspaceSupport.repoRoot().toFile();
            long freeSpace = root.getFreeSpace();
            long freeSpaceGB = freeSpace / (1024 * 1024 * 1024);
            
            logger.info("Free disk space: {} GB", freeSpaceGB);
            
            if (freeSpaceGB < 5) {
                return new RepairResult(false, "Low disk space: " + freeSpaceGB + "GB");
            } else {
                return new RepairResult(true, "Sufficient disk space: " + freeSpaceGB + "GB");
            }
        } catch (Exception e) {
            logger.error("Error checking disk space", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult pruneDockerImages() {
        logger.info("Pruning Docker images");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "image", "prune", "-f");
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
                logger.info("Docker images pruned");
                return new RepairResult(true, "Images pruned: " + output);
            } else {
                logger.error("Failed to prune images");
                return new RepairResult(false, "Prune failed");
            }
        } catch (Exception e) {
            logger.error("Error pruning Docker images", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult rebuildDockerImage() {
        logger.info("Rebuilding Docker image");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "build", "-t", "algotrading-bot:latest", ".");
            pb.directory(workspaceSupport.backendDir().toFile());
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
                logger.info("Docker image rebuilt");
                return new RepairResult(true, "Image rebuilt");
            } else {
                logger.error("Failed to rebuild image");
                return new RepairResult(false, "Rebuild failed");
            }
        } catch (Exception e) {
            logger.error("Error rebuilding Docker image", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    public RepairResult checkDockerDaemon() {
        logger.info("Checking Docker daemon");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Docker daemon is running");
                return new RepairResult(true, "Docker daemon running");
            } else {
                logger.error("Docker daemon not running");
                return new RepairResult(false, "Docker daemon not running");
            }
        } catch (Exception e) {
            logger.error("Error checking Docker daemon", e);
            return new RepairResult(false, "Error: " + e.getMessage());
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
