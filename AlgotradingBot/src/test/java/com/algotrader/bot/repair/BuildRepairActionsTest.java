package com.algotrader.bot.repair;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildRepairActionsTest {
    private BuildRepairActions actions;

    @BeforeEach
    void setUp() {
        actions = new BuildRepairActions();
    }

    @Test
    void testCheckDiskSpace() {
        RepairResult result = actions.checkDiskSpace();
        assertNotNull(result);
        assertNotNull(result.getMessage());
    }
}
