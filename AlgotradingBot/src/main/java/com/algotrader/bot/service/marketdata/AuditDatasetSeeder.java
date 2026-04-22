package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.service.BacktestDatasetStorageService;
import com.algotrader.bot.service.HistoricalDataCsvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Automatically seeds the audit dataset into the normalized relational store if configured via environment.
 * This ensures that strategy audit reports can be run in fresh environments without manual re-uploads.
 */
@Component
public class AuditDatasetSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AuditDatasetSeeder.class);

    private final BacktestDatasetRepository backtestDatasetRepository;
    private final BacktestDatasetStorageService backtestDatasetStorageService;
    private final HistoricalDataCsvParser historicalDataCsvParser;

    @Value("${AUDIT_DATASET_FILE:}")
    private String auditDatasetFilePath;

    public AuditDatasetSeeder(BacktestDatasetRepository backtestDatasetRepository,
                              BacktestDatasetStorageService backtestDatasetStorageService,
                              HistoricalDataCsvParser historicalDataCsvParser) {
        this.backtestDatasetRepository = backtestDatasetRepository;
        this.backtestDatasetStorageService = backtestDatasetStorageService;
        this.historicalDataCsvParser = historicalDataCsvParser;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (auditDatasetFilePath == null || auditDatasetFilePath.isBlank()) {
            return;
        }

        Path path = Path.of(auditDatasetFilePath);
        if (!Files.exists(path)) {
            logger.warn("Audit dataset file configured but not found: {}", auditDatasetFilePath);
            return;
        }

        try {
            byte[] csvBytes = Files.readAllBytes(path);
            String checksum = sha256Hex(csvBytes);

            if (backtestDatasetRepository.findByChecksumSha256(checksum).isPresent()) {
                logger.info("Audit dataset already exists (checksum match). Skipping seeding.");
                return;
            }

            logger.info("Seeding audit dataset from: {}", auditDatasetFilePath);
            List<OHLCVData> candles = historicalDataCsvParser.parse(csvBytes);
            BacktestDataset dataset = backtestDatasetStorageService.storeImportedDataset(
                "Audit Dataset",
                path.getFileName().toString(),
                candles,
                checksum,
                "AUDIT_SEEDER"
            );
            logger.info("Audit dataset seeded successfully with ID: {}", dataset.getId());

        } catch (IOException e) {
            logger.error("Failed to read audit dataset file: {}", auditDatasetFilePath, e);
        } catch (Exception e) {
            logger.error("Failed to seed audit dataset", e);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
