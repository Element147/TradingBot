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
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyDatasetBackfillStartupParticipantTest {

    @Test
    void recoverPendingWork_backfillsOnlyDatasetsMissingNormalizedSegments() {
        BacktestDatasetRepository backtestDatasetRepository = mock(BacktestDatasetRepository.class);
        MarketDataCandleSegmentRepository marketDataCandleSegmentRepository = mock(MarketDataCandleSegmentRepository.class);
        LegacyMarketDataMigrationService legacyMarketDataMigrationService = mock(LegacyMarketDataMigrationService.class);
        OperatorAuditService operatorAuditService = mock(OperatorAuditService.class);
        LegacyDatasetBackfillStartupParticipant participant = new LegacyDatasetBackfillStartupParticipant(
            backtestDatasetRepository,
            marketDataCandleSegmentRepository,
            legacyMarketDataMigrationService,
            operatorAuditService
        );

        BacktestDataset alreadyMigrated = dataset(10L, "already-migrated");
        BacktestDataset legacyDataset = dataset(11L, "legacy-dataset");

        when(backtestDatasetRepository.findAll(any(Sort.class))).thenReturn(List.of(alreadyMigrated, legacyDataset));
        when(marketDataCandleSegmentRepository.existsByDatasetId(10L)).thenReturn(true);
        when(marketDataCandleSegmentRepository.existsByDatasetId(11L)).thenReturn(false);
        when(legacyMarketDataMigrationService.migrate(any(LegacyMarketDataMigrationRequest.class))).thenReturn(
            new LegacyMarketDataMigrationSummary(false, List.of(new LegacyMarketDataMigrationDatasetResult(
                11L,
                "legacy-dataset",
                "abc",
                3,
                "BTC/USDT",
                1,
                1,
                3,
                0,
                0,
                "MIGRATED",
                "Migrated."
            )))
        );
        when(legacyMarketDataMigrationService.reconcile(any(LegacyMarketDataMigrationRequest.class))).thenReturn(
            new LegacyMarketDataReconciliationSummary(List.of(new LegacyMarketDataReconciliationDatasetResult(
                11L,
                "legacy-dataset",
                "abc",
                3,
                "BTC/USDT",
                1,
                1,
                3,
                3,
                "2025-01-01T00:00",
                "2025-01-01T00:00",
                "2025-01-01T02:00",
                "2025-01-01T02:00",
                "expected",
                "actual",
                "RECONCILED",
                "No rollback required.",
                List.of()
            )))
        );

        int recovered = participant.recoverPendingWork();

        assertEquals(1, recovered);
        verify(legacyMarketDataMigrationService).migrate(any(LegacyMarketDataMigrationRequest.class));
        verify(legacyMarketDataMigrationService).reconcile(any(LegacyMarketDataMigrationRequest.class));
        verify(operatorAuditService).recordSuccess(
            eq("LEGACY_DATASET_BACKFILLED_ON_STARTUP"),
            eq("test"),
            eq("BACKTEST_DATASET"),
            eq("11"),
            eq("Backfilled dataset into normalized market-data tables with 3 candles across 1 segments.")
        );
        verify(operatorAuditService, never()).recordFailure(any(), any(), any(), any(), any());
    }

    private BacktestDataset dataset(Long id, String name) {
        BacktestDataset dataset = mock(BacktestDataset.class);
        when(dataset.getId()).thenReturn(id);
        when(dataset.getName()).thenReturn(name);
        return dataset;
    }
}
