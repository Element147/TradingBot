package com.algotrader.bot.repair;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepairWorkspaceSupportTest {

    @Test
    void composeServiceAllowlistResolvesSupportedAliases() {
        RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

        assertEquals("postgres", workspaceSupport.composeServiceFor("postgres"));
        assertEquals("algotrading-app", workspaceSupport.composeServiceFor("backend"));
        assertEquals("algotrading-app", workspaceSupport.composeServiceFor("app"));
    }

    @Test
    void composeServiceAllowlistRejectsUnsupportedServices() {
        RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

        assertThrows(IllegalArgumentException.class, () -> workspaceSupport.composeServiceFor("redis"));
        assertThrows(IllegalArgumentException.class, () -> workspaceSupport.containerNameFor("custom-container"));
    }

    @Test
    void scriptAllowlistRejectsUnsupportedScripts() {
        RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();

        assertThrows(IllegalArgumentException.class, () -> workspaceSupport.scriptPath("cleanup.ps1"));
    }

    @Test
    void stopPidIfPresentRejectsNonNumericPid(@TempDir Path tempDir) throws Exception {
        RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();
        Path pidFile = tempDir.resolve("invalid.pid");
        Files.writeString(pidFile, "abc");

        assertThrows(IllegalArgumentException.class, () -> workspaceSupport.stopPidIfPresent(pidFile));
    }

    @Test
    void stopPidIfPresentRejectsNonPositivePid(@TempDir Path tempDir) throws Exception {
        RepairWorkspaceSupport workspaceSupport = RepairWorkspaceSupport.detect();
        Path pidFile = tempDir.resolve("zero.pid");
        Files.writeString(pidFile, "0");

        assertThrows(IllegalArgumentException.class, () -> workspaceSupport.stopPidIfPresent(pidFile));
    }
}
