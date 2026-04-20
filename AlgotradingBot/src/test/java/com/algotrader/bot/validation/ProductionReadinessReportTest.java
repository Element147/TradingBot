package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductionReadinessReportTest {
    private ProductionReadinessReport report;

    @BeforeEach
    void setUp() {
        report = new ProductionReadinessReport();
    }

    @Test
    void testReportCreation() {
        assertNotNull(report);
        assertNotNull(report.getTimestamp());
    }

    @Test
    void testIsProductionReadyWithNoResults() {
        assertTrue(report.isProductionReady());
    }

    @Test
    void testIsProductionReadyWithPassedResults() {
        report.addRequirementResult("REQ-1", new ValidationResult(
            "REQ-1", "Test", ValidationStatus.PASSED, "Passed"
        ));
        assertTrue(report.isProductionReady());
    }

    @Test
    void testIsProductionReadyWithFailedResults() {
        report.addRequirementResult("REQ-1", new ValidationResult(
            "REQ-1", "Test", ValidationStatus.FAILED, "Failed"
        ));
        assertFalse(report.isProductionReady());
    }

    @Test
    void testCalculateOverallStatus() {
        report.addRequirementResult("REQ-1", new ValidationResult(
            "REQ-1", "Test", ValidationStatus.PASSED, "Passed"
        ));
        assertEquals("PRODUCTION READY", report.calculateOverallStatus());
        
        report.addRequirementResult("REQ-2", new ValidationResult(
            "REQ-2", "Test", ValidationStatus.FAILED, "Failed"
        ));
        assertEquals("NOT READY", report.calculateOverallStatus());
    }
}
