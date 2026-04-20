package com.algotrader.bot.validation;

import com.algotrader.bot.repair.RepairWorkspaceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;

public class BuildValidator {
    private static final Logger logger = LoggerFactory.getLogger(BuildValidator.class);
    private static final long MIN_JAR_SIZE_MB = 30;
    private static final long MAX_JAR_SIZE_MB = 100;
    private static final long MAX_IMAGE_SIZE_MB = 300;
    private final RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

    public ValidationResult validateBuild() {
        logger.info("Starting build validation");
        LocalDateTime start = LocalDateTime.now();
        
        ValidationResult jarBuildResult = validateJarBuild();
        if (jarBuildResult.isFailed()) {
            return jarBuildResult;
        }
        
        ValidationResult jarResult = validateJarFile(Paths.get("build/libs/algotrading-bot.jar"));
        if (jarResult.isFailed()) {
            return jarResult;
        }
        
        ValidationResult dockerResult = validateDockerImage("algotrading-bot:latest");
        if (dockerResult.isFailed()) {
            return dockerResult;
        }
        
        ValidationResult result = new ValidationResult(
            "REQ-7", 
            "Build Validation", 
            ValidationStatus.PASSED, 
            "All build validations passed"
        );
        result.setExecutionTime(Duration.between(start, LocalDateTime.now()));
        logger.info("Build validation completed successfully");
        return result;
    }

    public ValidationResult validateJarBuild() {
        logger.info("Validating JAR build");
        try {
            // Avoid `clean` here: nested Gradle invocations during test runs can wipe active task outputs.
            ProcessBuilder pb = new ProcessBuilder("gradlew.bat", "--no-daemon", "bootJar");
            pb.directory(workspaceSupport.backendDir().toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Gradle: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("JAR build successful");
                return new ValidationResult(
                    "REQ-7.1", 
                    "JAR Build", 
                    ValidationStatus.PASSED, 
                    "JAR built successfully"
                );
            } else {
                logger.error("JAR build failed with exit code: {}", exitCode);
                ValidationResult result = new ValidationResult(
                    "REQ-7.1", 
                    "JAR Build", 
                    ValidationStatus.FAILED, 
                    "JAR build failed with exit code: " + exitCode
                );
                result.addMetadata("output", output.toString());
                return result;
            }
        } catch (Exception e) {
            logger.error("Error during JAR build validation", e);
            return new ValidationResult(
                "REQ-7.1", 
                "JAR Build", 
                ValidationStatus.FAILED, 
                "Build validation error: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateJarFile(Path jarPath) {
        logger.info("Validating JAR file at: {}", jarPath);
        
        // Check existence
        if (!Files.exists(jarPath)) {
            logger.error("JAR file not found: {}", jarPath);
            return new ValidationResult(
                "REQ-7.2", 
                "JAR Exists", 
                ValidationStatus.FAILED, 
                "JAR file not found: " + jarPath
            );
        }
        
        // Check size
        try {
            long sizeBytes = Files.size(jarPath);
            long sizeMB = sizeBytes / (1024 * 1024);
            logger.info("JAR file size: {} MB", sizeMB);
            
            if (sizeMB < MIN_JAR_SIZE_MB || sizeMB > MAX_JAR_SIZE_MB) {
                logger.error("JAR size {} MB is outside acceptable range ({}-{} MB)", sizeMB, MIN_JAR_SIZE_MB, MAX_JAR_SIZE_MB);
                return new ValidationResult(
                    "REQ-7.3", 
                    "JAR Size", 
                    ValidationStatus.FAILED, 
                    String.format("JAR size %d MB is outside range %d-%d MB", sizeMB, MIN_JAR_SIZE_MB, MAX_JAR_SIZE_MB)
                );
            }
            
            // Validate contents
            ValidationResult contentsResult = validateJarContents(jarPath);
            if (contentsResult.isFailed()) {
                return contentsResult;
            }
            
            ValidationResult result = new ValidationResult(
                "REQ-7.2", 
                "JAR File Validation", 
                ValidationStatus.PASSED, 
                "JAR file valid (" + sizeMB + " MB)"
            );
            result.addMetadata("sizeMB", sizeMB);
            return result;
            
        } catch (IOException e) {
            logger.error("Error validating JAR file", e);
            return new ValidationResult(
                "REQ-7.2", 
                "JAR File Validation", 
                ValidationStatus.FAILED, 
                "Error reading JAR file: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateJarContents(Path jarPath) {
        logger.info("Validating JAR contents");
        try {
            ProcessBuilder pb = new ProcessBuilder("jar", "tf", jarPath.toString());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            boolean hasSpringBoot = false;
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("org/springframework/boot/")) {
                        hasSpringBoot = true;
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                logger.error("Failed to read JAR contents");
                return new ValidationResult(
                    "REQ-7.4", 
                    "JAR Contents", 
                    ValidationStatus.FAILED, 
                    "Failed to read JAR contents"
                );
            }
            
            if (!hasSpringBoot) {
                logger.error("JAR does not contain Spring Boot classes");
                return new ValidationResult(
                    "REQ-7.4", 
                    "JAR Contents", 
                    ValidationStatus.FAILED, 
                    "JAR missing Spring Boot classes"
                );
            }
            
            logger.info("JAR contents validated successfully");
            return new ValidationResult(
                "REQ-7.4", 
                "JAR Contents", 
                ValidationStatus.PASSED, 
                "JAR contains required Spring Boot classes"
            );
            
        } catch (Exception e) {
            logger.error("Error validating JAR contents", e);
            return new ValidationResult(
                "REQ-7.4", 
                "JAR Contents", 
                ValidationStatus.FAILED, 
                "Error validating JAR contents: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateDockerImage(String imageName) {
        logger.info("Validating Docker image: {}", imageName);
        
        // Build image
        ValidationResult buildResult = validateDockerBuild(imageName);
        if (buildResult.isFailed()) {
            return buildResult;
        }
        
        // Check size
        return validateDockerImageSize(imageName);
    }

    public ValidationResult validateDockerBuild(String imageName) {
        logger.info("Building Docker image: {}", imageName);
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "build", "-t", imageName, ".");
            pb.directory(workspaceSupport.backendDir().toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Docker: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Docker image built successfully");
                return new ValidationResult(
                    "REQ-7.5", 
                    "Docker Build", 
                    ValidationStatus.PASSED, 
                    "Docker image built successfully"
                );
            } else {
                logger.error("Docker build failed with exit code: {}", exitCode);
                ValidationResult result = new ValidationResult(
                    "REQ-7.5", 
                    "Docker Build", 
                    ValidationStatus.FAILED, 
                    "Docker build failed with exit code: " + exitCode
                );
                result.addMetadata("output", output.toString());
                return result;
            }
        } catch (Exception e) {
            logger.error("Error during Docker build validation", e);
            return new ValidationResult(
                "REQ-7.5", 
                "Docker Build", 
                ValidationStatus.FAILED, 
                "Docker build error: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateDockerImageSize(String imageName) {
        logger.info("Validating Docker image size: {}", imageName);
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "images", imageName, "--format", "{{.Size}}");
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
            
            if (exitCode != 0) {
                logger.error("Failed to get Docker image size");
                return new ValidationResult(
                    "REQ-7.6", 
                    "Docker Image Size", 
                    ValidationStatus.FAILED, 
                    "Failed to get Docker image size"
                );
            }
            
            String sizeStr = output.toString().trim();
            logger.info("Docker image size: {}", sizeStr);
            
            // Parse size (could be in MB or GB)
            long sizeMB = parseSizeToMB(sizeStr);
            
            if (sizeMB > MAX_IMAGE_SIZE_MB) {
                logger.error("Docker image size {} MB exceeds limit of {} MB", sizeMB, MAX_IMAGE_SIZE_MB);
                return new ValidationResult(
                    "REQ-7.6", 
                    "Docker Image Size", 
                    ValidationStatus.FAILED, 
                    String.format("Image size %d MB exceeds limit of %d MB", sizeMB, MAX_IMAGE_SIZE_MB)
                );
            }
            
            logger.info("Docker image size validation passed");
            ValidationResult result = new ValidationResult(
                "REQ-7.6", 
                "Docker Image Size", 
                ValidationStatus.PASSED, 
                "Image size " + sizeMB + " MB is within limit"
            );
            result.addMetadata("sizeMB", sizeMB);
            return result;
            
        } catch (Exception e) {
            logger.error("Error validating Docker image size", e);
            return new ValidationResult(
                "REQ-7.6", 
                "Docker Image Size", 
                ValidationStatus.FAILED, 
                "Error validating image size: " + e.getMessage()
            );
        }
    }

    private long parseSizeToMB(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        if (sizeStr.endsWith("GB")) {
            double gb = Double.parseDouble(sizeStr.replace("GB", "").trim());
            return (long) (gb * 1024);
        } else if (sizeStr.endsWith("MB")) {
            return (long) Double.parseDouble(sizeStr.replace("MB", "").trim());
        } else if (sizeStr.endsWith("KB")) {
            double kb = Double.parseDouble(sizeStr.replace("KB", "").trim());
            return (long) (kb / 1024);
        }
        return 0;
    }
}
