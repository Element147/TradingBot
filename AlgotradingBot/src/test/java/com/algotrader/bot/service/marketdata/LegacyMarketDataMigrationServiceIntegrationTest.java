package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LegacyMarketDataMigrationServiceIntegrationTest {

    @Autowired
    private LegacyMarketDataMigrationService migrationService;

    @Autowired
    private BacktestDatasetRepository backtestDatasetRepository;

    @Autowired
    private MarketDataSeriesRepository marketDataSeriesRepository;

    @Autowired
    private MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;

    @Autowired
    private MarketDataCandleRepository marketDataCandleRepository;

    @Test
    void migrate_dryRunReportsPlannedWorkWithoutWritingNormalizedRows() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Dry run dataset",
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
                2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
                2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
                """
        ));

        LegacyMarketDataMigrationSummary summary = migrationService.migrate(
            new LegacyMarketDataMigrationRequest(true, Set.of(dataset.getId()), null)
        );

        assertThat(summary.datasetResults()).hasSize(1);
        assertThat(summary.datasetResults().getFirst().status()).isEqualTo("DRY_RUN_READY");
        assertThat(summary.datasetResults().getFirst().migratedSeriesCount()).isEqualTo(1);
        assertThat(marketDataSeriesRepository.count()).isZero();
        assertThat(marketDataCandleSegmentRepository.count()).isZero();
        assertThat(marketDataCandleRepository.count()).isZero();
    }

    @Test
    void migrate_isIdempotentAcrossRepeatedRuns() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Legacy migration dataset",
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
                2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
                2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
                """
        ));

        LegacyMarketDataMigrationSummary firstRun = migrationService.migrate(
            new LegacyMarketDataMigrationRequest(false, Set.of(dataset.getId()), null)
        );

        assertThat(firstRun.datasetResults()).hasSize(1);
        assertThat(firstRun.datasetResults().getFirst().status()).isEqualTo("MIGRATED");
        assertThat(firstRun.datasetResults().getFirst().migratedSegmentCount()).isEqualTo(1);
        assertThat(firstRun.datasetResults().getFirst().insertedCandleCount()).isEqualTo(3);
        assertThat(marketDataSeriesRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleSegmentRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleRepository.count()).isEqualTo(3);

        LegacyMarketDataMigrationSummary secondRun = migrationService.migrate(
            new LegacyMarketDataMigrationRequest(false, Set.of(dataset.getId()), null)
        );

        assertThat(secondRun.datasetResults()).hasSize(1);
        assertThat(secondRun.datasetResults().getFirst().status()).isEqualTo("SKIPPED_ALREADY_MIGRATED");
        assertThat(secondRun.datasetResults().getFirst().insertedCandleCount()).isZero();
        assertThat(marketDataSeriesRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleSegmentRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleRepository.count()).isEqualTo(3);
    }

    @Test
    void migrate_rejectsUnsupportedLegacyIntervalsWithoutWritingRows() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Unsupported interval dataset",
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,SPY,500,501,499,500,10
                2025-01-01T00:07:00,SPY,500,502,499,501,11
                """
        ));

        LegacyMarketDataMigrationSummary summary = migrationService.migrate(
            new LegacyMarketDataMigrationRequest(false, Set.of(dataset.getId()), null)
        );

        assertThat(summary.datasetResults()).hasSize(1);
        assertThat(summary.datasetResults().getFirst().status()).isEqualTo("FAILED");
        assertThat(summary.datasetResults().getFirst().rejectedRowCount()).isEqualTo(2);
        assertThat(marketDataSeriesRepository.count()).isZero();
        assertThat(marketDataCandleSegmentRepository.count()).isZero();
        assertThat(marketDataCandleRepository.count()).isZero();
    }

    @Test
    void reconcile_reportsFullyMatchedDatasetAfterMigration() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Reconciled dataset",
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
                2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
                2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
                """
        ));

        migrationService.migrate(new LegacyMarketDataMigrationRequest(false, Set.of(dataset.getId()), null));

        LegacyMarketDataReconciliationSummary summary = migrationService.reconcile(
            new LegacyMarketDataMigrationRequest(true, Set.of(dataset.getId()), null)
        );

        assertThat(summary.datasetResults()).hasSize(1);
        assertThat(summary.datasetResults().getFirst().status()).isEqualTo("RECONCILED");
        assertThat(summary.datasetResults().getFirst().discrepancies()).isEmpty();
        assertThat(summary.failedDatasets()).isZero();
    }

    @Test
    void reconcile_reportsMissingNormalizedDataWithRollbackGuidance() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Unmigrated dataset",
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
                2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
                2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
                """
        ));

        LegacyMarketDataReconciliationSummary summary = migrationService.reconcile(
            new LegacyMarketDataMigrationRequest(true, Set.of(dataset.getId()), null)
        );

        assertThat(summary.datasetResults()).hasSize(1);
        assertThat(summary.datasetResults().getFirst().status()).isEqualTo("FAILED");
        assertThat(summary.datasetResults().getFirst().rollbackAction()).contains("Keep this dataset on the legacy CSV compatibility path");
        assertThat(summary.datasetResults().getFirst().discrepancies())
            .anyMatch(detail -> detail.contains("Missing normalized series"));
    }

    @Test
    void reconcile_reportsCountAndChecksumDriftAfterNormalizedMutation() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Drift dataset",
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
                2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
                2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
                """
        ));

        migrationService.migrate(new LegacyMarketDataMigrationRequest(false, Set.of(dataset.getId()), null));
        MarketDataCandle mutatedCandle = marketDataCandleRepository.findAll().getFirst();
        mutatedCandle.setClosePrice(new BigDecimal("999.00000000"));
        marketDataCandleRepository.saveAndFlush(mutatedCandle);

        LegacyMarketDataReconciliationSummary summary = migrationService.reconcile(
            new LegacyMarketDataMigrationRequest(true, Set.of(dataset.getId()), null)
        );

        assertThat(summary.datasetResults()).hasSize(1);
        assertThat(summary.datasetResults().getFirst().status()).isEqualTo("FAILED");
        assertThat(summary.datasetResults().getFirst().discrepancies())
            .anyMatch(detail -> detail.contains("Checksum mismatch"));
        assertThat(summary.datasetResults().getFirst().discrepancies())
            .anyMatch(detail -> detail.contains("Derived hash summary mismatch"));
    }

    private BacktestDataset dataset(String name, String csvBody) {
        BacktestDataset dataset = new BacktestDataset();
        dataset.setName(name);
        dataset.setOriginalFilename(name.replace(' ', '-').toLowerCase() + ".csv");
        dataset.setCsvData(csvBody.stripIndent().trim().getBytes());
        dataset.setRowCount(csvBody.lines().count() <= 1 ? 0 : (int) csvBody.lines().skip(1).count());
        dataset.setSymbolsCsv(csvBody.contains("BTC/USDT") ? "BTC/USDT" : "SPY");
        dataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        dataset.setDataEnd(LocalDateTime.parse("2025-01-01T02:00:00"));
        dataset.setChecksumSha256((name + "-checksum").repeat(8).substring(0, 64));
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);
        return dataset;
    }
}
