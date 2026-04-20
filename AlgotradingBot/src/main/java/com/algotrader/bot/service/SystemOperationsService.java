package com.algotrader.bot.service;

import com.algotrader.bot.controller.BackupResponse;
import com.algotrader.bot.controller.SystemInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class SystemOperationsService {

    private static final Logger logger = LoggerFactory.getLogger(SystemOperationsService.class);
    private static final DateTimeFormatter BACKUP_FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final String PG_DUMP_COMMAND = "pg_dump";
    private static final String DOCKER_COMMAND = "docker";
    private static final String POSTGRES_CONTAINER_NAME = "algotrading-postgres";

    private final DataSource dataSource;
    private final BuildProperties buildProperties;
    private final Path backupDirectory;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final OperatorAuditService operatorAuditService;
    private final LocalDateTime appStartTime = LocalDateTime.now();

    public SystemOperationsService(
        Optional<DataSource> dataSource,
        Optional<BuildProperties> buildProperties,
        @Value("${algotrading.system.backup-dir:backups}") String backupDirectory,
        @Value("${spring.datasource.username:}") String datasourceUsername,
        @Value("${spring.datasource.password:}") String datasourcePassword,
        OperatorAuditService operatorAuditService
    ) {
        this.dataSource = dataSource.orElse(null);
        this.buildProperties = buildProperties.orElse(null);
        this.backupDirectory = Paths.get(backupDirectory);
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.operatorAuditService = operatorAuditService;
    }

    public SystemInfoResponse getSystemInfo() {
        String version = buildProperties != null ? buildProperties.getVersion() : "local-dev";
        String deploymentDate = buildProperties != null && buildProperties.getTime() != null
            ? buildProperties.getTime().atOffset(ZoneOffset.UTC).toString()
            : appStartTime.toString();

        return new SystemInfoResponse(
            version,
            deploymentDate,
            getDatabaseStatus()
        );
    }

    public BackupResponse triggerBackup() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(BACKUP_FILE_TS);
        Path backupFile = backupDirectory.resolve("backup_" + timestamp + ".sql");

        try {
            Files.createDirectories(backupDirectory);
            BackupExecutionResult result = createDatabaseBackup(backupFile);
            long sizeBytes = Files.size(backupFile);
            operatorAuditService.recordSuccess(
                "SYSTEM_BACKUP_TRIGGERED",
                "test",
                "SYSTEM_BACKUP",
                backupFile.toAbsolutePath().toString(),
                "database=" + result.databaseProduct() + ",method=" + result.method() + ",sizeBytes=" + sizeBytes
            );
            return new BackupResponse(backupFile.toAbsolutePath().toString(), sizeBytes + " bytes");
        } catch (Exception ex) {
            cleanupPartialBackup(backupFile);
            logger.error("Failed to create backup file", ex);
            operatorAuditService.recordFailure(
                "SYSTEM_BACKUP_TRIGGERED",
                "test",
                "SYSTEM_BACKUP",
                null,
                ex.getMessage()
            );
            throw new IllegalStateException("Unable to create backup file", ex);
        }
    }

    private BackupExecutionResult createDatabaseBackup(Path backupFile) throws Exception {
        if (dataSource == null) {
            throw new IllegalStateException("No datasource is available for backup execution.");
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseProduct = metaData.getDatabaseProductName();
            if ("H2".equalsIgnoreCase(databaseProduct)) {
                createH2Backup(connection, backupFile);
                return new BackupExecutionResult("H2", "jdbc-script");
            }
            if ("PostgreSQL".equalsIgnoreCase(databaseProduct)) {
                JdbcBackupTarget target = JdbcBackupTarget.from(metaData.getURL(), datasourceUsername, datasourcePassword);
                createPostgresBackup(target, backupFile);
                return new BackupExecutionResult("PostgreSQL", "pg_dump");
            }
            throw new IllegalStateException("Unsupported database product for backup: " + databaseProduct);
        }
    }

    private void createH2Backup(Connection connection, Path backupFile) throws SQLException {
        String escapedPath = backupFile.toAbsolutePath().toString().replace("\\", "\\\\").replace("'", "''");
        try (var statement = connection.createStatement()) {
            statement.execute("SCRIPT TO '" + escapedPath + "'");
        }
    }

    private void createPostgresBackup(JdbcBackupTarget target, Path backupFile) throws Exception {
        Exception localPgDumpFailure = null;
        try {
            runLocalPgDump(target, backupFile);
            return;
        } catch (Exception ex) {
            localPgDumpFailure = ex;
            logger.warn("Local pg_dump backup failed: {}", ex.getMessage());
        }

        try {
            runDockerPgDump(target, backupFile);
        } catch (Exception dockerFailure) {
            String localFailureMessage = localPgDumpFailure == null ? "n/a" : localPgDumpFailure.getMessage();
            throw new IllegalStateException(
                "Unable to create PostgreSQL backup. Local pg_dump failed: "
                    + localFailureMessage
                    + ". Docker fallback failed: "
                    + dockerFailure.getMessage(),
                dockerFailure
            );
        }
    }

    private void runLocalPgDump(JdbcBackupTarget target, Path backupFile) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            PG_DUMP_COMMAND,
            "--clean",
            "--if-exists",
            "--no-owner",
            "--no-privileges",
            "-h",
            target.host(),
            "-p",
            String.valueOf(target.port()),
            "-U",
            target.username(),
            "-d",
            target.database(),
            "-f",
            backupFile.toAbsolutePath().toString()
        );
        if (!target.password().isBlank()) {
            processBuilder.environment().put("PGPASSWORD", target.password());
        }

        Process process = processBuilder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Local pg_dump timed out.");
        }

        String stderr = readStream(process.getErrorStream());
        if (process.exitValue() != 0) {
            throw new IllegalStateException(stderr.isBlank() ? "Local pg_dump exited with code " + process.exitValue() : stderr);
        }
    }

    private void runDockerPgDump(JdbcBackupTarget target, Path backupFile) throws Exception {
        Process process = new ProcessBuilder(
            DOCKER_COMMAND,
            "exec",
            POSTGRES_CONTAINER_NAME,
            "pg_dump",
            "--clean",
            "--if-exists",
            "--no-owner",
            "--no-privileges",
            "-U",
            target.username(),
            "-d",
            target.database()
        ).start();
        try (InputStream stdout = process.getInputStream();
             OutputStream fileOutput = Files.newOutputStream(backupFile)) {
            stdout.transferTo(fileOutput);
        }

        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Docker pg_dump timed out.");
        }

        String stderr = readStream(process.getErrorStream());
        if (process.exitValue() != 0) {
            throw new IllegalStateException(stderr.isBlank() ? "Docker pg_dump exited with code " + process.exitValue() : stderr);
        }
    }

    private String readStream(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private void cleanupPartialBackup(Path backupFile) {
        try {
            Files.deleteIfExists(backupFile);
        } catch (IOException cleanupError) {
            logger.warn("Unable to remove partial backup file {}: {}", backupFile, cleanupError.getMessage());
        }
    }

    private String getDatabaseStatus() {
        if (dataSource == null) {
            return "unavailable";
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (SQLException ex) {
            logger.warn("Database connectivity check failed: {}", ex.getMessage());
            return "DOWN";
        }
    }
    private record BackupExecutionResult(String databaseProduct, String method) {
    }

    private record JdbcBackupTarget(String host, int port, String database, String username, String password) {
        private static JdbcBackupTarget from(String jdbcUrl, String username, String password) {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException("Unsupported PostgreSQL JDBC URL for backup: " + jdbcUrl);
            }

            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String database = Optional.ofNullable(uri.getPath())
                .map(path -> path.startsWith("/") ? path.substring(1) : path)
                .filter(path -> !path.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("PostgreSQL JDBC URL does not include a database name."));

            String resolvedUsername = username == null ? "" : username.trim();
            if (resolvedUsername.isBlank()) {
                throw new IllegalArgumentException("Datasource username is required for PostgreSQL backup.");
            }

            return new JdbcBackupTarget(
                Optional.ofNullable(uri.getHost()).orElse("localhost"),
                uri.getPort() > 0 ? uri.getPort() : 5432,
                database,
                resolvedUsername,
                password == null ? "" : password
            );
        }
    }
}
