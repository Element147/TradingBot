package com.algotrader.bot.repair;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckRepairActionsTest {
    private HealthCheckRepairActions actions;

    @BeforeEach
    void setUp() {
        actions = new HealthCheckRepairActions();
    }

    @Test
    void testActionsCreation() {
        assertNotNull(actions);
    }
}
