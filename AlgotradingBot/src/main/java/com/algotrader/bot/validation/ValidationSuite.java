package com.algotrader.bot.validation;

import com.algotrader.bot.repair.RepairEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ValidationSuite {
    private static final Logger logger = LoggerFactory.getLogger(ValidationSuite.class);
    private static final Path DEFAULT_REPORT_DIRECTORY = Paths.get("build", "reports", "validation");
    
    private BuildValidator buildValidator;
    private OrchestrationValidator orchestrationValidator;
    private ApiValidator apiValidator;
    private ResourceValidator resourceValidator;
    private DataPersistenceValidator dataPersistenceValidator;
    private RepairEngine repairEngine;
    private final Path reportDirectory;

    public ValidationSuite() {
        this(
            new BuildValidator(),
            new OrchestrationValidator(),
            new ApiValidator(),
            new ResourceValidator(),
            new DataPersistenceValidator(),
            new RepairEngine(),
            DEFAULT_REPORT_DIRECTORY
        );
    }

    ValidationSuite(Path reportDirectory) {
        this(
            new BuildValidator(),
            new OrchestrationValidator(),
            new ApiValidator(),
            new ResourceValidator(),
            new DataPersistenceValidator(),
            new RepairEngine(),
            reportDirectory
        );
    }

    ValidationSuite(BuildValidator buildValidator,
                    OrchestrationValidator orchestrationValidator,
                    ApiValidator apiValidator,
                    ResourceValidator resourceValidator,
                    DataPersistenceValidator dataPersistenceValidator,
                    RepairEngine repairEngine,
                    Path reportDirectory) {
        this.buildValidator = Objects.requireNonNull(buildValidator);
        this.orchestrationValidator = Objects.requireNonNull(orchestrationValidator);
        this.apiValidator = Objects.requireNonNull(apiValidator);
        this.resourceValidator = Objects.requireNonNull(resourceValidator);
        this.dataPersistenceValidator = Objects.requireNonNull(dataPersistenceValidator);
        this.repairEngine = Objects.requireNonNull(repairEngine);
        this.reportDirectory = Objects.requireNonNull(reportDirectory);
    }

    public int runAllValidations() {
        logger.info("=== STARTING PRODUCTION VALIDATION SUITE ===");
        LocalDateTime start = LocalDateTime.now();
        
        ProductionReadinessReport report = new ProductionReadinessReport();
        Map<String, ValidationResult> results = new HashMap<>();
        
        // Phase 1: Build Validation
        logger.info("\n[1/6] Running Build Validation...");
        ValidationResult buildResult = runWithRepair(
            () -> buildValidator.validateBuild(),
            failure -> repairEngine.repairBuildFailure(failure)
        );
        results.put("build", buildResult);
        
        if (buildResult.isFailed()) {
            logger.error("Build validation failed. Cannot proceed.");
            return generateFinalReport(report, results, start, 1);
        }
        
        // Phase 2: Orchestration Validation
        logger.info("\n[2/6] Running Orchestration Validation...");
        ValidationResult orchResult = runWithRepair(
            () -> orchestrationValidator.validateServiceStartup(),
            failure -> repairEngine.repairOrchestrationFailure(failure)
        );
        results.put("orchestration", orchResult);
        
        if (orchResult.isFailed()) {
            logger.error("Orchestration validation failed. Cannot proceed.");
            return generateFinalReport(report, results, start, 2);
        }
        
        // Phase 3: API Validation
        logger.info("\n[3/6] Running API Validation...");
        ValidationResult apiHealthResult = runWithRepair(
            () -> apiValidator.validateHealthEndpoint(),
            failure -> repairEngine.repairApiFailure(failure)
        );
        results.put("api-health", apiHealthResult);
        
        ValidationResult apiStatusResult = apiValidator.validateStrategyStatus();
        results.put("api-status", apiStatusResult);
        
        ValidationResult apiLifecycleResult = apiValidator.validateStrategyLifecycle();
        results.put("api-lifecycle", apiLifecycleResult);
        
        // Phase 4: Resource Validation
        logger.info("\n[4/6] Running Resource Validation...");
        ValidationResult memoryResult = resourceValidator.validateMemoryUsage();
        results.put("memory", memoryResult);
        
        ValidationResult diskResult = resourceValidator.validateDiskUsage();
        results.put("disk", diskResult);
        
        ResourceMetrics resourceMetrics = resourceValidator.collectResourceMetrics();
        report.setResourceMetrics(resourceMetrics);
        
        // Phase 5: Data Persistence Validation
        logger.info("\n[5/6] Running Data Persistence Validation...");
        ValidationResult dbPersistResult = runWithRepair(
            () -> dataPersistenceValidator.validateDatabasePersistence(),
            failure -> repairEngine.repairHealthCheckFailure(failure)
        );
        results.put("db-persistence", dbPersistResult);
        
        ValidationResult appReconnectResult = dataPersistenceValidator.validateApplicationReconnection();
        results.put("app-reconnect", appReconnectResult);
        
        // Phase 6: Generate Report
        logger.info("\n[6/6] Generating Production Readiness Report...");
        return generateFinalReport(report, results, start, 0);
    }

    private ValidationResult runWithRepair(ValidationSupplier validator, RepairFunction repairer) {
        ValidationResult result = validator.validate();
        
        if (result.isFailed()) {
            logger.warn("Validation failed: {}. Attempting repair...", result.getMessage());
            
            for (int attempt = 1; attempt <= 3; attempt++) {
                logger.info("Repair attempt {}/3", attempt);
                
                repairer.repair(result);
                
                // Wait before retry
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Retry validation
                result = validator.validate();
                if (result.isPassed()) {
                    logger.info("Validation passed after repair attempt {}", attempt);
                    return result;
                }
            }
            
            logger.error("Validation failed after 3 repair attempts");
        }
        
        return result;
    }

    private int generateFinalReport(ProductionReadinessReport report, 
                                    Map<String, ValidationResult> results, 
                                    LocalDateTime start, 
                                    int exitCode) {
        report.aggregateValidationResults(results);
        
        String textReport = report.generateTextReport();
        logger.info("\n" + textReport);
        
        // Save to file
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.createDirectories(reportDirectory);
            Path reportPath = reportDirectory.resolve("production-readiness-" + timestamp + ".txt");
            report.saveToFile(reportPath);
            logger.info("Report saved to: {}", reportPath);
        } catch (IOException e) {
            logger.error("Failed to save report to file", e);
        }
        
        Duration totalTime = Duration.between(start, LocalDateTime.now());
        logger.info("Total validation time: {} minutes", totalTime.toMinutes());
        
        return report.isProductionReady() ? 0 : (exitCode != 0 ? exitCode : 1);
    }

    @FunctionalInterface
    interface ValidationSupplier {
        ValidationResult validate();
    }

    @FunctionalInterface
    interface RepairFunction {
        void repair(ValidationResult failure);
    }

    public static void main(String[] args) {
        ValidationSuite suite = new ValidationSuite();
        int exitCode = suite.runAllValidations();
        System.exit(exitCode);
    }
}
