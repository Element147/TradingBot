package com.algotrader.bot.analysis;

import com.algotrader.bot.backtest.BacktestSimulationEngine;
import com.algotrader.bot.backtest.BacktestSimulationRequest;
import com.algotrader.bot.backtest.BacktestSimulationResult;
import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.backtest.strategy.BacktestIndicatorCalculator;
import com.algotrader.bot.backtest.strategy.BacktestStrategy;
import com.algotrader.bot.backtest.strategy.BacktestStrategyDefinition;
import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode;
import com.algotrader.bot.controller.BacktestDetailsResponse;
import com.algotrader.bot.controller.BacktestHistoryPageResponse;
import com.algotrader.bot.controller.BacktestHistoryQuery;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestEquityPoint;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import com.algotrader.bot.repository.MarketDataImportJobRepository;
import com.algotrader.bot.service.BackendOperationMetrics;
import com.algotrader.bot.service.BacktestAlgorithmType;
import com.algotrader.bot.service.BacktestDatasetLifecycleService;
import com.algotrader.bot.service.BacktestDatasetStorageService;
import com.algotrader.bot.service.BacktestExecutionLifecycleService;
import com.algotrader.bot.service.BacktestExecutionService;
import com.algotrader.bot.service.BacktestResultQueryService;
import com.algotrader.bot.service.BacktestTelemetryService;
import com.algotrader.bot.service.HistoricalDataCsvParser;
import com.algotrader.bot.service.OperatorAuditService;
import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataImportExecutionService;
import com.algotrader.bot.service.marketdata.MarketDataImportJobResponseMapper;
import com.algotrader.bot.service.marketdata.MarketDataImportJobStatus;
import com.algotrader.bot.service.marketdata.MarketDataProvider;
import com.algotrader.bot.service.marketdata.MarketDataImportProgressService;
import com.algotrader.bot.service.marketdata.MarketDataImportService;
import com.algotrader.bot.service.marketdata.MarketDataProviderDefinition;
import com.algotrader.bot.service.marketdata.MarketDataProviderRegistry;
import com.algotrader.bot.service.marketdata.MarketDataQueryMode;
import com.algotrader.bot.service.marketdata.MarketDataQueryResult;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import com.algotrader.bot.websocket.WebSocketEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public final class BackendWorkflowProfileRunner {

    private static final String SAMPLE_DATASET_RESOURCE = "/sample-btc-eth-data.csv";

    private BackendWorkflowProfileRunner() {
    }

    public static void main(String[] args) throws IOException {
        Fixture fixture = buildFixture();
        ProfileReport report = profile(fixture);
        Path output = resolveOutputPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, renderReport(report), StandardCharsets.UTF_8);
        System.out.println(renderReport(report));
        System.out.println("Backend workflow profiling report written to " + output.toAbsolutePath());
    }

    private static ProfileReport profile(Fixture fixture) {
        BackendOperationMetrics metrics = new BackendOperationMetrics(new SimpleMeterRegistry());

        BacktestDatasetRepository datasetRepository = mockDatasetRepository(fixture.dataset());
        MarketDataQueryService marketDataQueryService = mockMarketDataQueryService(fixture.candles());
        BacktestTelemetryService telemetryService = new BacktestTelemetryService(
            marketDataQueryService,
            new BacktestIndicatorCalculator(),
            metrics
        );
        BacktestResultRepository resultRepository = mockResultRepository(fixture.result());
        BacktestResultQueryService queryService = new BacktestResultQueryService(
            resultRepository,
            datasetRepository,
            telemetryService,
            metrics
        );
        BacktestDatasetLifecycleService datasetLifecycleService = new BacktestDatasetLifecycleService(
            datasetRepository,
            resultRepository,
            Mockito.mock(OperatorAuditService.class),
            metrics
        );
        MarketDataProviderRegistry providerRegistry = mockProviderRegistry();
        MarketDataImportJobResponseMapper mapper = new MarketDataImportJobResponseMapper(providerRegistry);

        MarketDataImportProgressService progressService = new MarketDataImportProgressService(
            Mockito.mock(WebSocketEventPublisher.class),
            mapper,
            metrics
        );
        MarketDataImportService importService = new MarketDataImportService(
            mockImportJobRepository(buildImportJob()),
            providerRegistry,
            Mockito.mock(com.algotrader.bot.service.marketdata.MarketDataProviderCredentialService.class),
            Mockito.mock(OperatorAuditService.class),
            mapper,
            progressService,
            Mockito.mock(MarketDataImportExecutionService.class),
            metrics
        );

        BacktestExecutionService executionService = new BacktestExecutionService(
            new BacktestDatasetStorageService(datasetRepository, new HistoricalDataCsvParser()),
            mockSimulationEngine(),
            mockStrategyRegistry(),
            marketDataQueryService,
            mockExecutionLifecycle(fixture.result()),
            metrics
        );

        TimedResult<BacktestHistoryPageResponse> history = timed(
            () -> queryService.getHistory(new BacktestHistoryQuery(
                1,
                100,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ))
        );
        TimedResult<BacktestDetailsResponse> details = timed(() -> queryService.getDetails(fixture.result().getId()));
        TimedResult<List<?>> telemetry = timed(() -> telemetryService.buildTelemetry(fixture.result()));
        TimedResult<List<?>> datasets = timed(datasetLifecycleService::listDatasets);
        TimedResult<List<?>> jobs = timed(importService::listJobs);
        TimedResult<Void> publish = timed(() -> {
            progressService.publish(buildImportJob());
            return null;
        });
        TimedResult<Void> startup = timed(() -> {
            executionService.executeAsync(fixture.result().getId()).join();
            return null;
        });

        return new ProfileReport(
            history.durationMs(),
            details.durationMs(),
            serializeSize(details.value()),
            telemetry.durationMs(),
            datasets.durationMs(),
            jobs.durationMs(),
            publish.durationMs(),
            startup.durationMs()
        );
    }

    private static Fixture buildFixture() throws IOException {
        HistoricalDataCsvParser parser = new HistoricalDataCsvParser();
        List<OHLCVData> baseCandles = parser.parse(loadSampleDataset()).stream()
            .sorted(Comparator.comparing(OHLCVData::getTimestamp).thenComparing(OHLCVData::getSymbol))
            .toList();
        List<OHLCVData> candles = new ArrayList<>(baseCandles.size() * 90);
        for (int copy = 0; copy < 90; copy++) {
            for (OHLCVData candle : baseCandles) {
                candles.add(new OHLCVData(
                    candle.getTimestamp().plusHours((long) copy * 48),
                    candle.getSymbol(),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume()
                ));
            }
        }
        candles.sort(Comparator.comparing(OHLCVData::getTimestamp).thenComparing(OHLCVData::getSymbol));

        BacktestDataset dataset = new BacktestDataset();
        setId(dataset, 77L);
        dataset.setName("Profiling Dataset");
        dataset.setOriginalFilename("profiling.csv");
        dataset.setUploadedAt(LocalDateTime.parse("2026-03-25T10:00:00"));
        dataset.setRowCount(candles.size());
        dataset.setSymbolsCsv("BTC/USDT,ETH/USDT");
        dataset.setDataStart(candles.getFirst().getTimestamp());
        dataset.setDataEnd(candles.getLast().getTimestamp());
        dataset.setChecksumSha256("profile-checksum");
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);

        BacktestResult result = new BacktestResult();
        result.setId(501L);
        result.setStrategyId(BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE.name());
        result.setDatasetId(dataset.getId());
        result.setDatasetName(dataset.getName());
        result.setExperimentName("Profiling Run");
        result.setExperimentKey("profiling-run");
        result.setSymbol("BTC/USDT");
        result.setTimeframe("1h");
        result.setStartDate(candles.getFirst().getTimestamp());
        result.setEndDate(candles.getLast().getTimestamp());
        result.setInitialBalance(new BigDecimal("10000.00"));
        result.setFinalBalance(new BigDecimal("11234.56"));
        result.setSharpeRatio(new BigDecimal("1.31"));
        result.setProfitFactor(new BigDecimal("1.42"));
        result.setWinRate(new BigDecimal("53.4"));
        result.setMaxDrawdown(new BigDecimal("11.8"));
        result.setTotalTrades(140);
        result.setValidationStatus(BacktestResult.ValidationStatus.PASSED);
        result.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        result.setExecutionStage(BacktestResult.ExecutionStage.COMPLETED);
        result.setFeesBps(10);
        result.setSlippageBps(3);
        result.setProgressPercent(100);
        result.setProcessedCandles(candles.size());
        result.setTotalCandles(candles.size());
        result.setCurrentDataTimestamp(candles.getLast().getTimestamp());
        result.setStatusMessage("Completed.");
        result.setTimestamp(LocalDateTime.parse("2026-03-25T10:05:00"));
        result.setLastProgressAt(LocalDateTime.parse("2026-03-25T10:05:00"));
        result.setStartedAt(LocalDateTime.parse("2026-03-25T10:00:00"));
        result.setCompletedAt(LocalDateTime.parse("2026-03-25T10:05:00"));

        List<OHLCVData> primary = candles.stream().filter(candle -> "BTC/USDT".equals(candle.getSymbol())).toList();
        for (int index = 0; index < primary.size(); index++) {
            BacktestEquityPoint point = new BacktestEquityPoint();
            point.setPointTimestamp(primary.get(index).getTimestamp());
            point.setEquity(new BigDecimal("10000").add(BigDecimal.valueOf(index * 3L)));
            point.setDrawdownPct(BigDecimal.ZERO);
            result.addEquityPoint(point);
        }
        int spacing = Math.max(10, primary.size() / 140);
        for (int index = 20; index + 4 < primary.size(); index += spacing) {
            BacktestTradeSeriesItem trade = new BacktestTradeSeriesItem();
            trade.setSymbol(primary.get(index).getSymbol());
            trade.setPositionSide(index % 3 == 0 ? PositionSide.SHORT : PositionSide.LONG);
            trade.setEntryTime(primary.get(index).getTimestamp());
            trade.setExitTime(primary.get(index + 4).getTimestamp());
            trade.setEntryPrice(primary.get(index).getClose());
            trade.setExitPrice(primary.get(index + 4).getClose());
            trade.setQuantity(new BigDecimal("1.000000"));
            trade.setEntryValue(primary.get(index).getClose());
            trade.setExitValue(primary.get(index + 4).getClose());
            trade.setReturnPct(new BigDecimal("0.0125"));
            result.addTradeSeriesItem(trade);
        }
        result.setTotalTrades(result.getTradeSeries().size());
        return new Fixture(dataset, result, candles);
    }

    private static BacktestDatasetRepository mockDatasetRepository(BacktestDataset dataset) {
        BacktestDatasetRepository repository = Mockito.mock(BacktestDatasetRepository.class);
        when(repository.findById(dataset.getId())).thenReturn(Optional.of(dataset));
        when(repository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(dataset));
        when(repository.findAllById(any())).thenReturn(List.of(dataset));
        return repository;
    }

    private static BacktestResultRepository mockResultRepository(BacktestResult result) {
        BacktestResultRepository repository = Mockito.mock(BacktestResultRepository.class);
        when(repository.findById(result.getId())).thenReturn(Optional.of(result));
        when(repository.findAllByOrderByTimestampDesc(any())).thenReturn(List.of(result));
        when(repository.summarizeDatasetUsage()).thenReturn(List.of());
        return repository;
    }

    private static MarketDataImportJobRepository mockImportJobRepository(MarketDataImportJob job) {
        MarketDataImportJobRepository repository = Mockito.mock(MarketDataImportJobRepository.class);
        when(repository.findTop25ByOrderByCreatedAtDesc()).thenReturn(List.of(job));
        return repository;
    }

    @SuppressWarnings("unchecked")
    private static MarketDataQueryService mockMarketDataQueryService(List<OHLCVData> candles) {
        MarketDataQueryService service = Mockito.mock(MarketDataQueryService.class);
        when(service.queryCandlesForDataset(any(), any(), any(), any(), any(Set.class), any(MarketDataQueryMode.class)))
            .thenAnswer(invocation -> new MarketDataQueryResult(
                candles.stream().map(candle -> new com.algotrader.bot.service.marketdata.MarketDataQueriedCandle(
                    candle.getTimestamp(),
                    candle.getSymbol(),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume(),
                    new com.algotrader.bot.service.marketdata.MarketDataCandleProvenance(
                        77L, 18L, 1L, 2L, "profile", "BINANCE", candle.getSymbol(),
                        invocation.getArgument(1, String.class),
                        "EXACT_RAW", "UPLOAD",
                        candle.getTimestamp().minusDays(1),
                        candle.getTimestamp().plusDays(1)
                    )
                )).toList(),
                List.of(),
                invocation.getArgument(1, String.class),
                invocation.getArgument(5, MarketDataQueryMode.class)
            ));
        return service;
    }

    private static BacktestSimulationEngine mockSimulationEngine() {
        BacktestSimulationEngine engine = Mockito.mock(BacktestSimulationEngine.class);
        when(engine.simulate(any(), any(BacktestSimulationRequest.class), any())).thenReturn(
            new BacktestSimulationResult(
                new BigDecimal("10042.00"),
                new BigDecimal("1.10"),
                new BigDecimal("1.20"),
                new BigDecimal("51.00"),
                new BigDecimal("8.50"),
                12,
                List.of(),
                List.of()
            )
        );
        return engine;
    }

    private static BacktestStrategyRegistry mockStrategyRegistry() {
        BacktestStrategyRegistry registry = Mockito.mock(BacktestStrategyRegistry.class);
        BacktestStrategy strategy = Mockito.mock(BacktestStrategy.class);
        when(strategy.definition()).thenReturn(new BacktestStrategyDefinition(
            BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE,
            "Trend First Adaptive Ensemble",
            "Profile stub",
            BacktestStrategySelectionMode.SINGLE_SYMBOL,
            200
        ));
        when(registry.getStrategy(BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE)).thenReturn(strategy);
        return registry;
    }

    private static MarketDataProviderRegistry mockProviderRegistry() {
        MarketDataProviderRegistry registry = Mockito.mock(MarketDataProviderRegistry.class);
        MarketDataProvider provider = Mockito.mock(MarketDataProvider.class);
        when(provider.definition()).thenReturn(new MarketDataProviderDefinition(
            "stub",
            "Stub Provider",
            "Profile stub",
            Set.of(MarketDataAssetType.CRYPTO),
            List.of("1h"),
            false,
            null,
            false,
            false,
            List.of("BTC/USDT"),
            null,
            null,
            null
        ));
        when(registry.get("stub")).thenReturn(provider);
        return registry;
    }

    private static BacktestExecutionLifecycleService mockExecutionLifecycle(BacktestResult result) {
        BacktestExecutionLifecycleService lifecycle = Mockito.mock(BacktestExecutionLifecycleService.class);
        when(lifecycle.markRunStarted(result.getId())).thenReturn(
            new BacktestExecutionLifecycleService.BacktestExecutionContext(
                result.getId(),
                result.getStrategyId(),
                result.getDatasetId(),
                result.getSymbol(),
                result.getTimeframe(),
                result.getStartDate(),
                result.getEndDate(),
                result.getInitialBalance(),
                result.getFeesBps(),
                result.getSlippageBps()
            )
        );
        return lifecycle;
    }

    private static MarketDataImportJob buildImportJob() {
        MarketDataImportJob job = new MarketDataImportJob();
        setId(job, 44L);
        job.setProviderId("stub");
        job.setAssetType(MarketDataAssetType.CRYPTO);
        job.setDatasetName("Profile Import");
        job.setSymbolsCsv("BTC/USDT,ETH/USDT");
        job.setTimeframe("1h");
        job.setStartDate(LocalDate.parse("2025-01-01"));
        job.setEndDate(LocalDate.parse("2025-03-01"));
        job.setAdjusted(false);
        job.setRegularSessionOnly(false);
        job.setStatus(MarketDataImportJobStatus.RUNNING);
        job.setStatusMessage("Running.");
        job.setCurrentSymbolIndex(1);
        job.setCurrentChunkStart(LocalDateTime.parse("2025-02-01T00:00:00"));
        job.setImportedRowCount(2000);
        job.setAttemptCount(1);
        job.setCreatedAt(LocalDateTime.parse("2026-03-25T10:00:00"));
        job.setUpdatedAt(LocalDateTime.parse("2026-03-25T10:02:00"));
        job.setStartedAt(LocalDateTime.parse("2026-03-25T10:00:10"));
        return job;
    }

    private static long serializeSize(BacktestDetailsResponse details) {
        try {
            return new ObjectMapper().findAndRegisterModules().writeValueAsBytes(details).length;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize detail payload", exception);
        }
    }

    private static String renderReport(ProfileReport report) {
        return """
            # Backend Workflow Profiling Report

            Generated by `backendWorkflowProfile` for task `1C.1`.

            | Operation | Duration ms | Notes |
            | --- | ---: | --- |
            | Backtest detail assembly | %s | Includes equity, trade series, provenance lookup, and telemetry reconstruction. |
            | Backtest telemetry reconstruction | %s | Indicator-series generation remains the heaviest pure read-side computation. |
            | Backtest execution startup | %s | Covers dataset lookup, candle query, request preparation, and simulated execution entry. |
            | Backtest history list | %s | Summary read path stays materially lighter than full detail assembly. |
            | Dataset inventory list | %s | Usage and duplicate classification adds work beyond a raw catalog scan. |
            | Import jobs list | %s | Lightweight list/read path. |
            | Import progress publish | %s | Mapping plus WebSocket publish path is not a dominant hotspot. |

            Representative backtest page-load decomposition:
            - Backend detail assembly: %s ms
            - Serialized detail payload size: %s
            - Frontend render phase: see `frontend/build/reports/backtest-page-profile/report.md` generated by `npm run profile:backtest`

            Ranked bottlenecks:
            1. Backtest detail assembly
            2. Backtest telemetry reconstruction
            3. Backtest execution startup
            4. Dataset inventory list
            5. Backtest history list
            6. Import jobs list
            7. Import progress publish

            Interpretation:
            - The detail endpoint is still the critical path for slow backtest review because it eagerly joins summary, provenance, equity, trades, and telemetry.
            - Telemetry reconstruction is the most expensive subcomponent inside detail assembly, so future work should split or lazy-load it before broad UI redesign.
            - Dataset inventory and import job reads are materially cheaper, which supports prioritizing backtest-detail slimming over broader catalog rewrites.
            """.formatted(
            format(report.backtestDetailsMs()),
            format(report.backtestTelemetryMs()),
            format(report.backtestExecutionStartupMs()),
            format(report.backtestHistoryMs()),
            format(report.datasetListingMs()),
            format(report.importJobsMs()),
            format(report.importProgressPublishMs()),
            format(report.backtestDetailsMs()),
            formatBytes(report.detailPayloadBytes())
        );
    }

    private static Path resolveOutputPath() {
        String configured = System.getProperty("backendWorkflowProfile.output");
        return configured == null || configured.isBlank()
            ? Path.of("build", "reports", "backend-workflow-profile", "report.md")
            : Path.of(configured);
    }

    private static byte[] loadSampleDataset() throws IOException {
        try (InputStream stream = BackendWorkflowProfileRunner.class.getResourceAsStream(SAMPLE_DATASET_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing sample dataset resource: " + SAMPLE_DATASET_RESOURCE);
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return (!content.isEmpty() && content.charAt(0) == '\uFEFF' ? content.substring(1) : content)
                .getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void setId(Object target, long id) {
        try {
            Field idField = target.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set synthetic id", exception);
        }
    }

    private static <T> TimedResult<T> timed(CheckedSupplier<T> supplier) {
        long startedAt = System.nanoTime();
        try {
            return new TimedResult<>(supplier.get(), (System.nanoTime() - startedAt) / 1_000_000.0);
        } catch (Exception exception) {
            throw new IllegalStateException("Profiling measurement failed", exception);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatBytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
    }

    private record Fixture(BacktestDataset dataset, BacktestResult result, List<OHLCVData> candles) {
    }

    private record ProfileReport(
        double backtestHistoryMs,
        double backtestDetailsMs,
        long detailPayloadBytes,
        double backtestTelemetryMs,
        double datasetListingMs,
        double importJobsMs,
        double importProgressPublishMs,
        double backtestExecutionStartupMs
    ) {
    }

    private record TimedResult<T>(T value, double durationMs) {
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
