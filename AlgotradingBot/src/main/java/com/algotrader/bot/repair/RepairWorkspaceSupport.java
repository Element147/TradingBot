package com.algotrader.bot.repair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RepairWorkspaceSupport {

    private static final List<Integer> MANAGED_PORTS = List.of(5432, 8080, 5173);
    private static final String COMPOSE_PROJECT_NAME = "algotradingbot";
    private static final String MANAGED_NETWORK_NAME = COMPOSE_PROJECT_NAME + "_algotrading-network";

    private final Path repoRoot;
    private final Path backendDir;
    private final Path composeFile;
    private final Path pidDirectory;

    private RepairWorkspaceSupport(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.backendDir = repoRoot.resolve("AlgotradingBot");
        this.composeFile = backendDir.resolve("compose.yaml");
        this.pidDirectory = repoRoot.resolve(".pids");
    }

    public static RepairWorkspaceSupport detect() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("run.ps1")) && Files.exists(cursor.resolve("AlgotradingBot").resolve("compose.yaml"))) {
                return new RepairWorkspaceSupport(cursor);
            }
            cursor = cursor.getParent();
        }

        throw new IllegalStateException("Unable to locate repository root for repair automation from " + current);
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public Path backendDir() {
        return backendDir;
    }

    public Path composeFile() {
        return composeFile;
    }

    public String composeProjectName() {
        return COMPOSE_PROJECT_NAME;
    }

    public String managedNetworkName() {
        return MANAGED_NETWORK_NAME;
    }

    public Path pidFile(String name) {
        return pidDirectory.resolve(name + ".pid");
    }

    public Path scriptPath(String scriptName) {
        return repoRoot.resolve(resolveManagedScript(scriptName));
    }

    public String composeServiceFor(String serviceName) {
        return resolveManagedService(serviceName).composeService();
    }

    public List<Integer> managedPorts() {
        return MANAGED_PORTS;
    }

    public String containerNameFor(String serviceName) {
        return resolveManagedService(serviceName).containerName();
    }

    public Map<Integer, String> findListeningProcesses(List<Integer> ports) {
        Map<Integer, String> conflicts = new LinkedHashMap<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("netstat", "-ano", "-p", "tcp");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            List<String> lines = new ArrayList<>();
            try (var reader = process.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            process.waitFor();

            for (Integer port : ports) {
                String portToken = ":" + port;
                for (String line : lines) {
                    if (line.contains(portToken) && line.contains("LISTENING")) {
                        String[] tokens = line.trim().split("\\s+");
                        if (tokens.length >= 5) {
                            conflicts.put(port, "pid=" + tokens[tokens.length - 1]);
                        } else {
                            conflicts.put(port, "in-use");
                        }
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to inspect listening ports: " + ex.getMessage(), ex);
        }

        return conflicts;
    }

    public void stopPidIfPresent(Path pidFile) throws IOException, InterruptedException {
        if (!Files.exists(pidFile)) {
            return;
        }

        String pidValue = Files.readString(pidFile).trim();
        if (pidValue.isBlank()) {
            return;
        }

        int pid = parseManagedPid(pidValue);
        Process process = new ProcessBuilder("taskkill", "/PID", Integer.toString(pid), "/F")
            .redirectErrorStream(true)
            .start();
        process.waitFor();
    }

    private ManagedService resolveManagedService(String serviceName) {
        return switch (serviceName == null ? "" : serviceName.trim().toLowerCase()) {
            case "postgres" -> ManagedService.POSTGRES;
            case "algotrading-app", "app", "backend" -> ManagedService.APP;
            default -> throw new IllegalArgumentException("Unsupported managed service: " + serviceName);
        };
    }

    private String resolveManagedScript(String scriptName) {
        return switch (scriptName == null ? "" : scriptName.trim().toLowerCase()) {
            case "run.ps1", "stop.ps1", "run-all.ps1", "stop-all.ps1" -> scriptName;
            default -> throw new IllegalArgumentException("Unsupported managed script: " + scriptName);
        };
    }

    private int parseManagedPid(String pidValue) {
        try {
            int pid = Integer.parseInt(pidValue);
            if (pid <= 0) {
                throw new IllegalArgumentException("PID must be positive");
            }
            return pid;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("PID file does not contain a valid integer: " + pidValue, ex);
        }
    }

    private enum ManagedService {
        POSTGRES("postgres", "algotrading-postgres"),
        APP("algotrading-app", "algotrading-app");

        private final String composeService;
        private final String containerName;

        ManagedService(String composeService, String containerName) {
            this.composeService = composeService;
            this.containerName = containerName;
        }

        String composeService() {
            return composeService;
        }

        String containerName() {
            return containerName;
        }
    }
}
