package com.algotrader.bot.repair;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrchestrationRepairActionsTest {
    private OrchestrationRepairActions actions;

    @BeforeEach
    void setUp() {
        actions = new OrchestrationRepairActions();
    }

    @Test
    void testActionsCreation() {
        assertNotNull(actions);
    }
}
