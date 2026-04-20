package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceValidatorTest {
    private ResourceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResourceValidator();
    }

    @Test
    void testResourceMetricsCreation() {
        ResourceMetrics metrics = new ResourceMetrics();
        assertNotNull(metrics);
        assertNotNull(metrics.getApplicationMetrics());
        assertNotNull(metrics.getDatabaseMetrics());
    }

    @Test
    void testResourceMetricsWithinLimits() {
        ResourceMetrics metrics = new ResourceMetrics();
        metrics.getApplicationMetrics().setMemoryUsageMB(400);
        metrics.getDatabaseMetrics().setMemoryUsageMB(200);
        metrics.setTotalMemoryUsageMB(600);
        
        assertTrue(metrics.isWithinLimits());
    }

    @Test
    void testResourceMetricsExceedsLimits() {
        ResourceMetrics metrics = new ResourceMetrics();
        metrics.getApplicationMetrics().setMemoryUsageMB(600); // Exceeds 512MB
        metrics.setTotalMemoryUsageMB(600);
        
        assertFalse(metrics.isWithinLimits());
    }
}
