package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StabilityValidatorTest {
    private StabilityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StabilityValidator();
    }

    @Test
    void testStabilityMetricsCreation() {
        StabilityMetrics metrics = new StabilityMetrics();
        assertNotNull(metrics);
        assertEquals(0, metrics.getContainerRestarts());
        assertEquals(0, metrics.getErrorLogCount());
        assertTrue(metrics.isDatabaseConnectionStable());
    }

    @Test
    void testStabilityMetricsIsStable() {
        StabilityMetrics metrics = new StabilityMetrics();
        assertTrue(metrics.isStable());
        
        metrics.setContainerRestarts(1);
        assertFalse(metrics.isStable());
    }
}
