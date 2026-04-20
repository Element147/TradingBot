package com.algotrader.bot.validation;

import com.algotrader.bot.repair.RepairEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ValidationSuiteTest {

    private ValidationSuite validationSuite;
    @TempDir
    Path reportDirectory;

    @BeforeEach
    void setUp() {
        validationSuite = new ValidationSuite(
            new StubBuildValidator(),
            new StubOrchestrationValidator(),
            new StubApiValidator(),
            new StubResourceValidator(),
            new StubDataPersistenceValidator(),
            new RepairEngine(),
            reportDirectory
        );
    }

    @Test
    void testValidationSuiteCreation() {
        assertNotNull(validationSuite);
    }

    @Test
    void testRunAllValidationsReturnsExitCode() throws IOException {
        int exitCode = validationSuite.runAllValidations();

        assertEquals(0, exitCode);
        try (Stream<Path> reportFiles = Files.list(reportDirectory)) {
            assertTrue(reportFiles.findAny().isPresent(), "Validation suite should write a report");
        }
    }

    private static ValidationResult passedResult(String requirementId, String requirementName, String message) {
        return new ValidationResult(requirementId, requirementName, ValidationStatus.PASSED, message);
    }

    private static final class StubBuildValidator extends BuildValidator {
        @Override
        public ValidationResult validateBuild() {
            return passedResult("REQ-7", "Build Validation", "Build validation stub passed");
        }
    }

    private static final class StubOrchestrationValidator extends OrchestrationValidator {
        @Override
        public ValidationResult validateServiceStartup() {
            return passedResult("REQ-8", "Service Orchestration", "Orchestration stub passed");
        }
    }

    private static final class StubApiValidator extends ApiValidator {
        @Override
        public ValidationResult validateHealthEndpoint() {
            return passedResult("REQ-9.1", "Health Endpoint", "Health endpoint stub passed");
        }

        @Override
        public ValidationResult validateStrategyStatus() {
            return passedResult("REQ-9.3", "Strategy Status", "Strategy status stub passed");
        }

        @Override
        public ValidationResult validateStrategyLifecycle() {
            return passedResult("REQ-9.5", "Strategy Lifecycle", "Strategy lifecycle stub passed");
        }
    }

    private static final class StubResourceValidator extends ResourceValidator {
        @Override
        public ValidationResult validateMemoryUsage() {
            return passedResult("REQ-11", "Memory Usage", "Memory usage stub passed");
        }

        @Override
        public ValidationResult validateDiskUsage() {
            return passedResult("REQ-11.8", "Disk Usage", "Disk usage stub passed");
        }

        @Override
        public ResourceMetrics collectResourceMetrics() {
            ResourceMetrics metrics = new ResourceMetrics();
            metrics.setTotalMemoryUsageMB(0);
            metrics.setTotalDiskUsageGB(0);
            return metrics;
        }
    }

    private static final class StubDataPersistenceValidator extends DataPersistenceValidator {
        @Override
        public ValidationResult validateDatabasePersistence() {
            return passedResult("REQ-14.1", "Database Persistence", "Database persistence stub passed");
        }

        @Override
        public ValidationResult validateApplicationReconnection() {
            return passedResult("REQ-14.5", "Application Reconnection", "Application reconnection stub passed");
        }
    }
}
