package com.algotrader.bot.repair;

import com.algotrader.bot.validation.ValidationResult;
import com.algotrader.bot.validation.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepairEngineTest {
    private RepairEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RepairEngine();
    }

    @Test
    void testShouldRetry() {
        assertTrue(engine.shouldRetry(0));
        assertTrue(engine.shouldRetry(1));
        assertTrue(engine.shouldRetry(2));
        assertFalse(engine.shouldRetry(3));
        assertFalse(engine.shouldRetry(4));
    }

    @Test
    void testSelectRepairActionForBuildFailure() {
        ValidationResult failure = new ValidationResult(
            "REQ-7.1", 
            "JAR Build", 
            ValidationStatus.FAILED, 
            "Build failed"
        );
        
        RepairAction action = engine.selectRepairAction(failure);
        assertEquals(RepairAction.CLEAN_GRADLE_CACHE, action);
    }

    @Test
    void testSelectRepairActionForDockerFailure() {
        ValidationResult failure = new ValidationResult(
            "REQ-7.5", 
            "Docker Build", 
            ValidationStatus.FAILED, 
            "Docker build failed"
        );
        
        RepairAction action = engine.selectRepairAction(failure);
        assertEquals(RepairAction.PRUNE_DOCKER_IMAGES, action);
    }

    @Test
    void testSelectRepairActionForOrchestrationFailure() {
        ValidationResult failure = new ValidationResult(
            "REQ-8.1", 
            "Service Start", 
            ValidationStatus.FAILED, 
            "Services failed to start"
        );
        
        RepairAction action = engine.selectRepairAction(failure);
        assertEquals(RepairAction.RESTART_SERVICES, action);
    }

    @Test
    void testSelectRepairActionForPortConflict() {
        ValidationResult failure = new ValidationResult(
            "REQ-8.2",
            "Port Conflict",
            ValidationStatus.FAILED,
            "Port 8080 is already in use"
        );

        RepairAction action = engine.selectRepairAction(failure);
        assertEquals(RepairAction.RESOLVE_PORT_CONFLICTS, action);
    }

    @Test
    void testSelectRepairActionForNetworkOrphanFailure() {
        ValidationResult failure = new ValidationResult(
            "REQ-8.3",
            "Docker Network",
            ValidationStatus.FAILED,
            "Orphan containers detected on managed network"
        );

        RepairAction action = engine.selectRepairAction(failure);
        assertEquals(RepairAction.CLEANUP_ORPHANED_CONTAINERS, action);
    }
}
