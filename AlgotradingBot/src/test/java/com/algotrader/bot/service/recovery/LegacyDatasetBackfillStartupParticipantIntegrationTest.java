package com.algotrader.bot.service.recovery;

import com.algotrader.bot.controller.BacktestSymbolTelemetryResponse;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import com.algotrader.bot.service.BacktestExecutionService;
import com.algotrader.bot.service.BacktestTelemetryService;
import com.algotrader.bot.service.marketdata.MarketDataQueriedCandle;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LegacyDatasetBackfillStartupParticipantIntegrationTest {

    @Autowired
    private LegacyDatasetBackfillStartupParticipant legacyDatasetBackfillStartupParticipant;

    @Autowired
    private BacktestDatasetRepository backtestDatasetRepository;

    @Autowired
    private BacktestResultRepository backtestResultRepository;

    @Autowired
    private MarketDataSeriesRepository marketDataSeriesRepository;

    @Autowired
    private MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;

    @Autowired
    private MarketDataCandleRepository marketDataCandleRepository;

    @Autowired
    private MarketDataQueryService marketDataQueryService;

    @Autowired
    private BacktestExecutionService backtestExecutionService;

    @Autowired
    private BacktestTelemetryService backtestTelemetryService;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        backtestResultRepository.deleteAll();
        marketDataCandleRepository.deleteAll();
        marketDataCandleSegmentRepository.deleteAll();
        marketDataSeriesRepository.deleteAll();
        backtestDatasetRepository.deleteAll();
    }

    @Test
    void recoverPendingWork_backfillsLegacyDatasetAndBacktestRuntimeStaysOnNormalizedStore() throws Exception {
        byte[] csvData = buildLegacyCsv(24);
        BacktestDataset dataset = new BacktestDataset();
        dataset.setName("legacy-runtime-cutover");
        dataset.setOriginalFilename("legacy-runtime-cutover.csv");
        dataset.setCsvData(csvData);
        dataset.setRowCount(24);
        dataset.setSymbolsCsv("BTC/USDT");
        dataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        dataset.setDataEnd(LocalDateTime.parse("2025-01-01T23:00:00"));
        dataset.setUploadedAt(LocalDateTime.parse("2025-01-06T00:00:00"));
        dataset.setChecksumSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);
        dataset = backtestDatasetRepository.saveAndFlush(dataset);

        double cacheMissesBefore = gaugeValue("algotrading.backtests.dataset_cache.misses");

        assertThat(legacyDatasetBackfillStartupParticipant.recoverPendingWork()).isBetween(0, 1);
        assertThat(marketDataCandleSegmentRepository.existsByDatasetId(dataset.getId())).isTrue();

        List<MarketDataQueriedCandle> normalizedBeforeCorruption = marketDataQueryService.loadCandlesForDataset(
            dataset.getId(),
            "1h",
            dataset.getDataStart(),
            dataset.getDataEnd(),
            Set.of("BTC/USDT")
        );
        assertThat(normalizedBeforeCorruption).hasSize(24);
        assertThat(normalizedBeforeCorruption)
            .extracting(candle -> candle.provenance().sourceType())
            .containsOnly("LEGACY_DATASET");
        assertThat(gaugeValue("algotrading.backtests.dataset_cache.misses")).isEqualTo(cacheMissesBefore);

        BacktestDataset brokenCsvDataset = backtestDatasetRepository.findById(dataset.getId()).orElseThrow();
        brokenCsvDataset.setCsvData("not,a,valid,csv".getBytes());
        backtestDatasetRepository.saveAndFlush(brokenCsvDataset);

        BacktestResult pending = new BacktestResult(
            "BUY_AND_HOLD",
            "BTC/USDT",
            dataset.getDataStart(),
            dataset.getDataEnd(),
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            BacktestResult.ValidationStatus.PENDING
        );
        pending.setExecutionStatus(BacktestResult.ExecutionStatus.PENDING);
        pending.setExecutionStage(BacktestResult.ExecutionStage.QUEUED);
        pending.setDatasetId(dataset.getId());
        pending.setDatasetName(dataset.getName());
        pending.setTimeframe("1h");
        pending.setFeesBps(10);
        pending.setSlippageBps(3);
        Long backtestId = backtestResultRepository.saveAndFlush(pending).getId();

        backtestExecutionService.executeAsync(backtestId);
        BacktestResult completed = waitForBacktest(backtestId);

        assertThat(completed.getExecutionStatus()).isEqualTo(BacktestResult.ExecutionStatus.COMPLETED);
        assertThat(completed.getExecutionStage()).isEqualTo(BacktestResult.ExecutionStage.COMPLETED);
        assertThat(completed.getTotalCandles()).isEqualTo(24);

        BacktestResult telemetryInput = new BacktestResult();
        telemetryInput.setDatasetId(completed.getDatasetId());
        telemetryInput.setStrategyId(completed.getStrategyId());
        telemetryInput.setSymbol(completed.getSymbol());
        telemetryInput.setTimeframe(completed.getTimeframe());
        telemetryInput.setStartDate(completed.getStartDate());
        telemetryInput.setEndDate(completed.getEndDate());
        telemetryInput.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);

        List<BacktestSymbolTelemetryResponse> telemetry = backtestTelemetryService.buildTelemetry(telemetryInput);
        assertThat(telemetry).hasSize(1);
        assertThat(telemetry.getFirst().points()).hasSize(24);
        assertThat(telemetry.getFirst().provenance())
            .extracting(provenance -> provenance.sourceType())
            .containsOnly("LEGACY_DATASET");

        List<MarketDataQueriedCandle> normalizedAfterCorruption = marketDataQueryService.loadCandlesForDataset(
            dataset.getId(),
            "1h",
            dataset.getDataStart(),
            dataset.getDataEnd(),
            Set.of("BTC/USDT")
        );
        assertThat(normalizedAfterCorruption).hasSize(24);
        assertThat(normalizedAfterCorruption)
            .extracting(MarketDataQueriedCandle::close)
            .containsExactlyElementsOf(normalizedBeforeCorruption.stream().map(MarketDataQueriedCandle::close).toList());
        assertThat(gaugeValue("algotrading.backtests.dataset_cache.misses")).isEqualTo(cacheMissesBefore);
    }

    private byte[] buildLegacyCsv(int hours) {
        StringBuilder csv = new StringBuilder("timestamp,symbol,open,high,low,close,volume\n");
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        for (int index = 0; index < hours; index++) {
            int open = 100 + index;
            csv.append(start.plusHours(index))
                .append(",BTC/USDT,")
                .append(open)
                .append(',')
                .append(open + 1)
                .append(',')
                .append(open - 1)
                .append(',')
                .append(open)
                .append(",1\n");
        }
        return csv.toString().getBytes();
    }

    private BacktestResult waitForBacktest(Long id) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            BacktestResult result = backtestResultRepository.findById(id).orElseThrow();
            if (result.getExecutionStatus() == BacktestResult.ExecutionStatus.COMPLETED
                || result.getExecutionStatus() == BacktestResult.ExecutionStatus.FAILED) {
                return result;
            }
            Thread.sleep(100);
        }
        return backtestResultRepository.findById(id).orElseThrow();
    }

    private double gaugeValue(String name) {
        Double value = meterRegistry.get(name).gauge().value();
        return value == null ? 0.0d : value;
    }
}
