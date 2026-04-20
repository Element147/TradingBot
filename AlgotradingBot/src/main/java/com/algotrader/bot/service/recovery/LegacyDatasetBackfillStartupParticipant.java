package com.algotrader.bot.service.recovery;

import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.service.OperatorAuditService;
import com.algotrader.bot.service.marketdata.LegacyMarketDataMigrationDatasetResult;
import com.algotrader.bot.service.marketdata.LegacyMarketDataMigrationRequest;
import com.algotrader.bot.service.marketdata.LegacyMarketDataMigrationService;
import com.algotrader.bot.service.marketdata.LegacyMarketDataMigrationSummary;
import com.algotrader.bot.service.marketdata.LegacyMarketDataReconciliationDatasetResult;
import com.algotrader.bot.service.marketdata.LegacyMarketDataReconciliationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class LegacyDatasetBackfillStartupParticipant implements StartupRecoveryParticipant {

    private static final Logger logger = LoggerFactory.getLogger(LegacyDatasetBackfillStartupParticipant.class);

    private final BacktestDatasetRepository backtestDatasetRepository;
    private final MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;
    private final LegacyMarketDataMigrationService legacyMarketDataMigrationService;
    private final OperatorAuditService operatorAuditService;

    public LegacyDatasetBackfillStartupParticipant(BacktestDatasetRepository backtestDatasetRepository,
                                                   MarketDataCandleSegmentRepository marketDataCandleSegmentRepository,
                                                   LegacyMarketDataMigrationService legacyMarketDataMigrationService,
                                                   OperatorAuditService operatorAuditService) {
        this.backtestDatasetRepository = backtestDatasetRepository;
        this.marketDataCandleSegmentRepository = marketDataCandleSegmentRepository;
        this.legacyMarketDataMigrationService = legacyMarketDataMigrationService;
        this.operatorAuditService = operatorAuditService;
    }

    @Override
    public String participantName() {
        return "legacyDatasetBackfill";
    }

    @Override
    public int recoverPendingWork() {
        int backfilledDatasets = 0;
        for (BacktestDataset dataset : backtestDatasetRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            if (marketDataCandleSegmentRepository.existsByDatasetId(dataset.getId())) {
                continue;
            }

            LegacyMarketDataMigrationDatasetResult migrationResult = migrateDataset(dataset);
            if (migrationResult.failed()) {
                recordFailure(dataset, "Migration failed: " + migrationResult.message());
                continue;
            }

            LegacyMarketDataReconciliationDatasetResult reconciliationResult = reconcileDataset(dataset);
            if (reconciliationResult.failed()) {
                recordFailure(
                    dataset,
                    "Reconciliation failed after startup backfill. " + reconciliationResult.rollbackAction()
                );
                continue;
            }

            backfilledDatasets++;
            String detail = "Backfilled dataset into normalized market-data tables with "
                + migrationResult.insertedCandleCount()
                + " candles across "
                + migrationResult.migratedSegmentCount()
                + " segments.";
            logger.info("Startup backfill completed for legacy dataset {}: {}", dataset.getId(), detail);
            operatorAuditService.recordSuccess(
                "LEGACY_DATASET_BACKFILLED_ON_STARTUP",
                "test",
                "BACKTEST_DATASET",
                String.valueOf(dataset.getId()),
                detail
            );
        }
        return backfilledDatasets;
    }

    private LegacyMarketDataMigrationDatasetResult migrateDataset(BacktestDataset dataset) {
        LegacyMarketDataMigrationSummary summary = legacyMarketDataMigrationService.migrate(requestFor(dataset));
        return summary.datasetResults().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing migration result for dataset " + dataset.getId()));
    }

    private LegacyMarketDataReconciliationDatasetResult reconcileDataset(BacktestDataset dataset) {
        LegacyMarketDataReconciliationSummary summary = legacyMarketDataMigrationService.reconcile(requestFor(dataset));
        return summary.datasetResults().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing reconciliation result for dataset " + dataset.getId()));
    }

    private LegacyMarketDataMigrationRequest requestFor(BacktestDataset dataset) {
        return new LegacyMarketDataMigrationRequest(false, Set.of(dataset.getId()), 1);
    }

    private void recordFailure(BacktestDataset dataset, String detail) {
        logger.warn("Startup backfill failed for legacy dataset {}: {}", dataset.getId(), detail);
        operatorAuditService.recordFailure(
            "LEGACY_DATASET_BACKFILL_FAILED_ON_STARTUP",
            "test",
            "BACKTEST_DATASET",
            String.valueOf(dataset.getId()),
            detail
        );
    }
}
