package com.algotrader.bot.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProductionReadinessReport {
    private static final Logger logger = LoggerFactory.getLogger(ProductionReadinessReport.class);
    
    private final Map<String, ValidationResult> requirementResults;
    private ResourceMetrics resourceMetrics;
    private StabilityMetrics stabilityMetrics;
    private final LocalDateTime timestamp;
    private String environment;

    public ProductionReadinessReport() {
        this.requirementResults = new LinkedHashMap<>();
        this.timestamp = LocalDateTime.now();
    }

    public boolean isProductionReady() {
        return requirementResults.values().stream().allMatch(ValidationResult::isPassed);
    }

    public void aggregateValidationResults(Map<String, ValidationResult> results) {
        logger.info("Aggregating {} validation results", results.size());
        this.requirementResults.putAll(results);
    }

    public String calculateOverallStatus() {
        return isProductionReady() ? "PRODUCTION READY" : "NOT READY";
    }

    public String generateTextReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== PRODUCTION READINESS REPORT ===\n");
        report.append("Generated: ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        report.append("Environment: ").append(environment != null ? environment : "Docker Production").append("\n\n");
        
        report.append("BUILD VALIDATION:\n");
        appendRequirementResults(report, "REQ-7");
        
        report.append("\nORCHESTRATION VALIDATION:\n");
        appendRequirementResults(report, "REQ-8");
        
        report.append("\nAPI VALIDATION:\n");
        appendRequirementResults(report, "REQ-9");
        
        report.append("\nSTABILITY VALIDATION:\n");
        appendRequirementResults(report, "REQ-10");
        if (stabilityMetrics != null) {
            report.append("  Average Memory: ").append(String.format("%.2f", stabilityMetrics.getAverageMemoryUsageMB())).append(" MB\n");
            report.append("  Average CPU: ").append(String.format("%.2f", stabilityMetrics.getAverageCpuUsagePercent())).append("%\n");
        }
        
        report.append("\nRESOURCE VALIDATION:\n");
        appendRequirementResults(report, "REQ-11");
        if (resourceMetrics != null) {
            report.append("  Application: ").append(resourceMetrics.getApplicationMetrics().getMemoryUsageMB()).append(" MB\n");
            report.append("  Database: ").append(resourceMetrics.getDatabaseMetrics().getMemoryUsageMB()).append(" MB\n");
            report.append("  Total: ").append(resourceMetrics.getTotalMemoryUsageMB()).append(" MB\n");
        }
        
        report.append("\nDATA PERSISTENCE VALIDATION:\n");
        appendRequirementResults(report, "REQ-14");
        
        report.append("\n");
        report.append("OVERALL STATUS: ").append(isProductionReady() ? "[PASS] PRODUCTION READY" : "[FAIL] NOT READY").append("\n\n");
        
        long passed = requirementResults.values().stream().filter(ValidationResult::isPassed).count();
        long total = requirementResults.size();
        report.append(passed).append("/").append(total).append(" requirements passed.\n");
        
        if (isProductionReady()) {
            report.append("System is ready for production deployment.\n");
        } else {
            report.append("System is NOT ready for production. Review failures above.\n");
        }
        
        return report.toString();
    }

    private void appendRequirementResults(StringBuilder report, String reqPrefix) {
        requirementResults.entrySet().stream()
            .filter(e -> e.getKey().startsWith(reqPrefix))
            .forEach(e -> {
                ValidationResult result = e.getValue();
                String icon = result.isPassed() ? "[PASS]" : "[FAIL]";
                report.append("  ").append(icon).append(" ")
                      .append(result.getRequirementName()).append(": ")
                      .append(result.getMessage()).append("\n");
            });
    }

    public String generateJsonReport() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("  \"environment\": \"").append(environment != null ? environment : "Docker Production").append("\",\n");
        json.append("  \"overallStatus\": \"").append(calculateOverallStatus()).append("\",\n");
        json.append("  \"productionReady\": ").append(isProductionReady()).append(",\n");
        json.append("  \"totalRequirements\": ").append(requirementResults.size()).append(",\n");
        
        long passed = requirementResults.values().stream().filter(ValidationResult::isPassed).count();
        json.append("  \"requirementsPassed\": ").append(passed).append(",\n");
        json.append("  \"requirementsFailed\": ").append(requirementResults.size() - passed).append("\n");
        json.append("}\n");
        
        return json.toString();
    }

    public void saveToFile(Path outputPath) throws IOException {
        String report = generateTextReport();
        Files.writeString(outputPath, report);
        logger.info("Report saved to: {}", outputPath);
    }

    // Getters and setters
    public Map<String, ValidationResult> getRequirementResults() { return requirementResults; }
    public void addRequirementResult(String key, ValidationResult result) { 
        this.requirementResults.put(key, result); 
    }
    
    public ResourceMetrics getResourceMetrics() { return resourceMetrics; }
    public void setResourceMetrics(ResourceMetrics resourceMetrics) { 
        this.resourceMetrics = resourceMetrics; 
    }
    
    public StabilityMetrics getStabilityMetrics() { return stabilityMetrics; }
    public void setStabilityMetrics(StabilityMetrics stabilityMetrics) { 
        this.stabilityMetrics = stabilityMetrics; 
    }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
}
