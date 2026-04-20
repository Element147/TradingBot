package com.algotrader.bot.analysis;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.backtest.strategy.BacktestIndicatorCalculator;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestEquityPoint;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.service.BacktestDatasetCandleCache;
import com.algotrader.bot.service.BacktestDatasetStorageService;
import com.algotrader.bot.service.BacktestTelemetryService;
import com.algotrader.bot.service.BackendOperationMetrics;
import com.algotrader.bot.service.HistoricalDataCsvParser;
import com.algotrader.bot.service.marketdata.MarketDataCsvSupport;
import com.algotrader.bot.service.marketdata.MarketDataQueryMetrics;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import com.algotrader.bot.service.marketdata.MarketDataResampler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public final class LegacyMarketDataFlowAuditRunner {

    private static final String SAMPLE_DATASET_RESOURCE = "/sample-btc-eth-data.csv";
    private static final String CSV_HEADER = "timestamp,symbol,open,high,low,close,volume\n";
    private static final List<DatasetScale> SCALES = List.of(
        new DatasetScale("small", 1),
        new DatasetScale("medium", 25),
        new DatasetScale("large", 200)
    );

    private LegacyMarketDataFlowAuditRunner() {
    }

    public static void main(String[] args) throws IOException {
        HistoricalDataCsvParser parser = new HistoricalDataCsvParser();
        List<OHLCVData> baseCandles = parser.parse(loadSampleDataset());
        List<MetricRow> rows = new ArrayList<>();

        for (DatasetScale scale : SCALES) {
            SyntheticDataset syntheticDataset = buildSyntheticDataset(baseCandles, scale);
            rows.add(measureScale(scale, syntheticDataset));
        }

        String report = renderReport(rows);
        Path output = resolveOutputPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("Legacy market-data flow audit report written to " + output.toAbsolutePath());
    }

    private static MetricRow measureScale(DatasetScale scale, SyntheticDataset syntheticDataset) {
        HistoricalDataCsvParser parser = new HistoricalDataCsvParser();
        MarketDataCsvSupport csvSupport = new MarketDataCsvSupport();

        BacktestDatasetRepository storageRepository = mockDatasetRepository();
        BacktestDatasetStorageService storageService = new BacktestDatasetStorageService(storageRepository, parser);
        BacktestDataset storedDataset = storageService.storeImportedDataset(
            "Legacy Audit " + scale.label(),
            scale.label() + "-legacy-audit.csv",
            syntheticDataset.csvBytes()
        );
        wireRepository(storageRepository, storedDataset);

        ParseMeasurement parseMeasurement = measureParse(parser, syntheticDataset.csvBytes());
        double uploadImportMs = averageMillis(3, () -> freshStorageService(parser).storeImportedDataset(
            "Upload " + scale.label(),
            scale.label() + "-upload.csv",
            syntheticDataset.csvBytes()
        ));
        double downloadMs = averageMillis(5, () -> storageService.downloadDataset(storedDataset.getId()));
        double cacheMissMs = averageMillis(3, () -> freshCache(parser).getOrParse(storedDataset));
        BacktestDatasetCandleCache warmCache = freshCache(parser);
        warmCache.getOrParse(storedDataset);
        double cacheHitMs = averageMillis(5, () -> warmCache.getOrParse(storedDataset));
        WarmupMeasurement warmupMeasurement = measureWarmup(storedDataset, syntheticDataset, false);
        WarmupMeasurement warmCacheMeasurement = measureWarmup(storedDataset, syntheticDataset, true);
        TelemetryMeasurement telemetryCold = measureTelemetry(storedDataset, syntheticDataset, false);
        TelemetryMeasurement telemetryWarm = measureTelemetry(storedDataset, syntheticDataset, true);
        double stagedAppendMs = averageMillis(2, () -> stageCsvBatches(csvSupport, syntheticDataset.batches()));

        return new MetricRow(
            scale.label(),
            syntheticDataset.csvBytes().length,
            syntheticDataset.candles().size(),
            parseMeasurement.parseMs(),
            parseMeasurement.heapDeltaBytes(),
            uploadImportMs,
            downloadMs,
            cacheMissMs,
            cacheHitMs,
            warmupMeasurement.durationMs(),
            warmCacheMeasurement.durationMs(),
            warmupMeasurement.simulationRows(),
            telemetryCold.durationMs(),
            telemetryWarm.durationMs(),
            telemetryCold.telemetryPoints(),
            telemetryCold.actionMarkers(),
            telemetryCold.indicatorPoints(),
            stagedAppendMs
        );
    }

    private static WarmupMeasurement measureWarmup(BacktestDataset dataset,
                                                   SyntheticDataset syntheticDataset,
                                                   boolean preloadCache) {
        HistoricalDataCsvParser parser = new HistoricalDataCsvParser();
        BacktestDatasetCandleCache cache = new BacktestDatasetCandleCache(parser);
        MarketDataResampler resampler = new MarketDataResampler();
        if (preloadCache) {
            cache.getOrParse(dataset);
        }

        TimedResult<List<OHLCVData>> timed = timed(() -> {
            List<OHLCVData> candles = cache.getOrParse(dataset);
            List<OHLCVData> filtered = candles.stream()
                .filter(candle -> !candle.getTimestamp().isBefore(syntheticDataset.start()))
                .filter(candle -> !candle.getTimestamp().isAfter(syntheticDataset.end()))
                .sorted(Comparator.comparing(OHLCVData::getTimestamp))
                .toList();
            String primarySymbol = syntheticDataset.primarySymbol();
            List<OHLCVData> scoped = filtered.stream()
                .filter(candle -> candle.getSymbol().equals(primarySymbol))
                .toList();
            return resampler.resample(scoped, "1h");
        });

        return new WarmupMeasurement(timed.durationMs(), timed.value().size());
    }

    private static TelemetryMeasurement measureTelemetry(BacktestDataset dataset,
                                                         SyntheticDataset syntheticDataset,
                                                         boolean preloadCache) {
        HistoricalDataCsvParser parser = new HistoricalDataCsvParser();
        BacktestDatasetRepository repository = mockDatasetRepository();
        wireRepository(repository, dataset);
        BacktestDatasetCandleCache cache = new BacktestDatasetCandleCache(parser);
        if (preloadCache) {
            cache.getOrParse(dataset);
        }
        MarketDataQueryService marketDataQueryService = new MarketDataQueryService(
            Mockito.mock(MarketDataCandleRepository.class),
            new BacktestDatasetStorageService(repository, parser),
            cache,
            new MarketDataResampler(),
            new MarketDataQueryMetrics(new SimpleMeterRegistry())
        );
        BacktestTelemetryService telemetryService = new BacktestTelemetryService(
            marketDataQueryService,
            new BacktestIndicatorCalculator(),
            new BackendOperationMetrics(new SimpleMeterRegistry())
        );
        BacktestResult result = buildCompletedResult(dataset, syntheticDataset);

        TimedResult<List<com.algotrader.bot.controller.BacktestSymbolTelemetryResponse>> timed =
            timed(() -> telemetryService.buildTelemetry(result));

        int telemetryPoints = timed.value().stream().mapToInt(item -> item.points().size()).sum();
        int actionMarkers = timed.value().stream().mapToInt(item -> item.actions().size()).sum();
        int indicatorPoints = timed.value().stream()
            .flatMap(item -> item.indicators().stream())
            .mapToInt(series -> series.points().size())
            .sum();

        return new TelemetryMeasurement(timed.durationMs(), telemetryPoints, actionMarkers, indicatorPoints);
    }

    private static BacktestResult buildCompletedResult(BacktestDataset dataset, SyntheticDataset syntheticDataset) {
        BacktestResult result = new BacktestResult();
        result.setId(42L);
        result.setStrategyId("TREND_FIRST_ADAPTIVE_ENSEMBLE");
        result.setDatasetId(dataset.getId());
        result.setDatasetName(dataset.getName());
        result.setSymbol(syntheticDataset.primarySymbol());
        result.setTimeframe("1h");
        result.setStartDate(syntheticDataset.start());
        result.setEndDate(syntheticDataset.end());
        result.setInitialBalance(new BigDecimal("10000.00"));
        result.setFinalBalance(new BigDecimal("11250.00"));
        result.setSharpeRatio(new BigDecimal("1.42"));
        result.setProfitFactor(new BigDecimal("1.35"));
        result.setWinRate(new BigDecimal("54.2"));
        result.setMaxDrawdown(new BigDecimal("12.4"));
        result.setTotalTrades(24);
        result.setValidationStatus(BacktestResult.ValidationStatus.PASSED);
        result.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        result.setExecutionStage(BacktestResult.ExecutionStage.COMPLETED);
        result.setFeesBps(10);
        result.setSlippageBps(3);
        result.setProgressPercent(100);
        result.setProcessedCandles(syntheticDataset.primarySymbolCandles().size());
        result.setTotalCandles(syntheticDataset.primarySymbolCandles().size());
        result.setCurrentDataTimestamp(syntheticDataset.end());
        result.setStatusMessage("Completed.");
        result.setTimestamp(LocalDateTime.now());
        result.setLastProgressAt(LocalDateTime.now());
        result.setStartedAt(LocalDateTime.now().minusMinutes(5));
        result.setCompletedAt(LocalDateTime.now());

        List<OHLCVData> relevantCandles = syntheticDataset.primarySymbolCandles();
        BigDecimal startingEquity = result.getInitialBalance();
        BigDecimal priceDriftDivisor = BigDecimal.valueOf(Math.max(1, relevantCandles.size()));
        BigDecimal runningPeak = startingEquity;

        for (int index = 0; index < relevantCandles.size(); index++) {
            OHLCVData candle = relevantCandles.get(index);
            BigDecimal equity = startingEquity.add(
                BigDecimal.valueOf(index)
                    .multiply(new BigDecimal("1250.00"))
                    .divide(priceDriftDivisor, 8, java.math.RoundingMode.HALF_UP)
            );
            if (equity.compareTo(runningPeak) > 0) {
                runningPeak = equity;
            }

            BacktestEquityPoint point = new BacktestEquityPoint();
            point.setPointTimestamp(candle.getTimestamp());
            point.setEquity(equity);
            point.setDrawdownPct(runningPeak.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : runningPeak.subtract(equity)
                    .divide(runningPeak, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, java.math.RoundingMode.HALF_UP));
            result.addEquityPoint(point);
        }

        int tradeSpacing = Math.max(20, relevantCandles.size() / 24);
        for (int startIndex = 5; startIndex + tradeSpacing < relevantCandles.size(); startIndex += tradeSpacing) {
            OHLCVData entry = relevantCandles.get(startIndex);
            OHLCVData exit = relevantCandles.get(Math.min(relevantCandles.size() - 1, startIndex + Math.max(4, tradeSpacing / 3)));
            BacktestTradeSeriesItem trade = new BacktestTradeSeriesItem();
            trade.setSymbol(entry.getSymbol());
            trade.setPositionSide(PositionSide.LONG);
            trade.setEntryTime(entry.getTimestamp());
            trade.setExitTime(exit.getTimestamp());
            trade.setEntryPrice(entry.getClose());
            trade.setExitPrice(exit.getClose());
            trade.setQuantity(new BigDecimal("1.00000000"));
            trade.setEntryValue(entry.getClose());
            trade.setExitValue(exit.getClose());
            trade.setReturnPct(exit.getClose()
                .subtract(entry.getClose())
                .divide(entry.getClose(), 8, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, java.math.RoundingMode.HALF_UP));
            result.addTradeSeriesItem(trade);
        }

        result.setTotalTrades(result.getTradeSeries().size());
        return result;
    }

    private static byte[] stageCsvBatches(MarketDataCsvSupport csvSupport, List<List<OHLCVData>> batches) {
        byte[] staged = null;
        for (List<OHLCVData> batch : batches) {
            staged = csvSupport.appendRows(staged, batch);
        }
        return staged;
    }

    private static ParseMeasurement measureParse(HistoricalDataCsvParser parser, byte[] csvBytes) {
        forceGc();
        long usedBefore = usedMemoryBytes();
        TimedResult<List<OHLCVData>> timed = timed(() -> parser.parse(csvBytes));
        long usedAfter = usedMemoryBytes();
        return new ParseMeasurement(timed.durationMs(), Math.max(0L, usedAfter - usedBefore), timed.value().size());
    }

    private static SyntheticDataset buildSyntheticDataset(List<OHLCVData> baseCandles, DatasetScale scale) {
        List<OHLCVData> sortedBaseCandles = baseCandles.stream()
            .sorted(Comparator.comparing(OHLCVData::getTimestamp).thenComparing(OHLCVData::getSymbol))
            .toList();
        LocalDateTime baseStart = sortedBaseCandles.getFirst().getTimestamp();
        LocalDateTime baseEnd = sortedBaseCandles.getLast().getTimestamp();
        Duration batchOffset = Duration.between(baseStart, baseEnd).plus(findMinimumGap(sortedBaseCandles));
        List<List<OHLCVData>> batches = new ArrayList<>(scale.copies());
        List<OHLCVData> combined = new ArrayList<>(sortedBaseCandles.size() * scale.copies());

        for (int copy = 0; copy < scale.copies(); copy++) {
            Duration offset = batchOffset.multipliedBy(copy);
            List<OHLCVData> shiftedBatch = new ArrayList<>(sortedBaseCandles.size());
            for (OHLCVData candle : sortedBaseCandles) {
                shiftedBatch.add(new OHLCVData(
                    candle.getTimestamp().plus(offset),
                    candle.getSymbol(),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume()
                ));
            }
            batches.add(List.copyOf(shiftedBatch));
            combined.addAll(shiftedBatch);
        }

        combined.sort(Comparator.comparing(OHLCVData::getTimestamp).thenComparing(OHLCVData::getSymbol));
        byte[] csvBytes = toCsv(combined);
        String primarySymbol = combined.getFirst().getSymbol();
        List<OHLCVData> primarySymbolCandles = combined.stream()
            .filter(candle -> candle.getSymbol().equals(primarySymbol))
            .toList();

        return new SyntheticDataset(
            List.copyOf(combined),
            List.copyOf(batches),
            csvBytes,
            combined.getFirst().getTimestamp(),
            combined.getLast().getTimestamp(),
            primarySymbol,
            primarySymbolCandles
        );
    }

    private static Duration findMinimumGap(List<OHLCVData> candles) {
        Duration minimumGap = null;
        Map<String, LocalDateTime> previousBySymbol = new LinkedHashMap<>();
        for (OHLCVData candle : candles) {
            LocalDateTime previous = previousBySymbol.put(candle.getSymbol(), candle.getTimestamp());
            if (previous == null) {
                continue;
            }
            Duration gap = Duration.between(previous, candle.getTimestamp());
            if (gap.isZero() || gap.isNegative()) {
                continue;
            }
            if (minimumGap == null || gap.compareTo(minimumGap) < 0) {
                minimumGap = gap;
            }
        }
        return minimumGap == null ? Duration.ofMinutes(15) : minimumGap;
    }

    private static byte[] toCsv(List<OHLCVData> candles) {
        StringBuilder builder = new StringBuilder(Math.max(1024, candles.size() * 64));
        builder.append(CSV_HEADER);
        for (OHLCVData candle : candles) {
            builder.append(candle.getTimestamp()).append(',')
                .append(candle.getSymbol()).append(',')
                .append(candle.getOpen()).append(',')
                .append(candle.getHigh()).append(',')
                .append(candle.getLow()).append(',')
                .append(candle.getClose()).append(',')
                .append(candle.getVolume()).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static BacktestDatasetStorageService freshStorageService(HistoricalDataCsvParser parser) {
        return new BacktestDatasetStorageService(mockDatasetRepository(), parser);
    }

    private static BacktestDatasetCandleCache freshCache(HistoricalDataCsvParser parser) {
        return new BacktestDatasetCandleCache(parser);
    }

    private static BacktestDatasetRepository mockDatasetRepository() {
        BacktestDatasetRepository repository = Mockito.mock(BacktestDatasetRepository.class);
        when(repository.save(any(BacktestDataset.class))).thenAnswer(invocation -> {
            BacktestDataset saved = invocation.getArgument(0, BacktestDataset.class);
            if (saved.getId() == null) {
                setDatasetId(saved, 1L);
            }
            if (saved.getUploadedAt() == null) {
                saved.setUploadedAt(LocalDateTime.now());
            }
            return saved;
        });
        return repository;
    }

    private static void wireRepository(BacktestDatasetRepository repository, BacktestDataset dataset) {
        when(repository.findById(dataset.getId())).thenReturn(Optional.of(dataset));
    }

    private static void setDatasetId(BacktestDataset dataset, long id) {
        try {
            Field idField = BacktestDataset.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(dataset, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set synthetic dataset id for audit", exception);
        }
    }

    private static String renderReport(List<MetricRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Legacy Market-Data Flow Audit\n\n");
        builder.append("Generated by `legacyMarketDataFlowAudit` against the current CSV-backed dataset path.\n\n");
        builder.append("| Scale | Payload | Rows | Parse ms | Parse heap MB | Upload/import ms | Download ms | Cache miss ms | Cache hit ms | Warm-up cold ms | Warm-up warm ms | Warm-up rows | Telemetry cold ms | Telemetry warm ms | Telemetry points | Markers | Indicator points | Staged append ms |\n");
        builder.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (MetricRow row : rows) {
            builder.append("| ").append(row.label())
                .append(" | ").append(formatBytes(row.payloadBytes()))
                .append(" | ").append(row.rowCount())
                .append(" | ").append(formatDouble(row.parseMs()))
                .append(" | ").append(formatDouble(bytesToMegabytes(row.parseHeapDeltaBytes())))
                .append(" | ").append(formatDouble(row.uploadImportMs()))
                .append(" | ").append(formatDouble(row.downloadMs()))
                .append(" | ").append(formatDouble(row.cacheMissMs()))
                .append(" | ").append(formatDouble(row.cacheHitMs()))
                .append(" | ").append(formatDouble(row.warmupColdMs()))
                .append(" | ").append(formatDouble(row.warmupWarmMs()))
                .append(" | ").append(row.warmupRows())
                .append(" | ").append(formatDouble(row.telemetryColdMs()))
                .append(" | ").append(formatDouble(row.telemetryWarmMs()))
                .append(" | ").append(row.telemetryPoints())
                .append(" | ").append(row.actionMarkers())
                .append(" | ").append(row.indicatorPoints())
                .append(" | ").append(formatDouble(row.stagedAppendMs()))
                .append(" |\n");
        }
        builder.append("\n");
        builder.append("Notes:\n");
        builder.append("- `Upload/import ms` measures `BacktestDatasetStorageService.storeImportedDataset`, which reparses the full CSV before persistence.\n");
        builder.append("- `Warm-up` mirrors the current execution preparation path: cache lookup or parse, date filtering, single-symbol scoping, and timeframe resampling.\n");
        builder.append("- `Telemetry` measures `BacktestTelemetryService.buildTelemetry` for a completed result with equity and trade series attached.\n");
        builder.append("- `Staged append` measures repeated `MarketDataCsvSupport.appendRows` calls, which rebuild the accumulated CSV bytes on every import chunk.\n");
        builder.append("- Heap delta is an approximate post-GC JVM reading and should be treated as a directional signal, not an exact retained-size measurement.\n");
        return builder.toString();
    }

    private static <T> TimedResult<T> timed(GenericCheckedSupplier<T> supplier) {
        long startedAt = System.nanoTime();
        try {
            return new TimedResult<>(supplier.get(), nanosToMillis(System.nanoTime() - startedAt));
        } catch (Exception exception) {
            throw new IllegalStateException("Audit measurement failed", exception);
        }
    }

    private static double averageMillis(int iterations, CheckedRunnable runnable) {
        double total = 0.0;
        for (int index = 0; index < iterations; index++) {
            long startedAt = System.nanoTime();
            try {
                runnable.run();
            } catch (Exception exception) {
                throw new IllegalStateException("Audit measurement failed", exception);
            }
            total += nanosToMillis(System.nanoTime() - startedAt);
        }
        return total / iterations;
    }

    private static Path resolveOutputPath() {
        String configured = System.getProperty("legacyAudit.output");
        if (configured == null || configured.isBlank()) {
            return Path.of("build", "reports", "legacy-market-data-flow-audit", "report.md");
        }
        return Path.of(configured);
    }

    private static byte[] loadSampleDataset() throws IOException {
        try (InputStream stream = LegacyMarketDataFlowAuditRunner.class.getResourceAsStream(SAMPLE_DATASET_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing sample dataset resource: " + SAMPLE_DATASET_RESOURCE);
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() {
        for (int index = 0; index < 3; index++) {
            System.gc();
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double bytesToMegabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytesToMegabytes(bytes));
    }

    private record DatasetScale(String label, int copies) {
    }

    private record SyntheticDataset(
        List<OHLCVData> candles,
        List<List<OHLCVData>> batches,
        byte[] csvBytes,
        LocalDateTime start,
        LocalDateTime end,
        String primarySymbol,
        List<OHLCVData> primarySymbolCandles
    ) {
    }

    private record ParseMeasurement(double parseMs, long heapDeltaBytes, int parsedRows) {
    }

    private record WarmupMeasurement(double durationMs, int simulationRows) {
    }

    private record TelemetryMeasurement(double durationMs, int telemetryPoints, int actionMarkers, int indicatorPoints) {
    }

    private record MetricRow(
        String label,
        long payloadBytes,
        int rowCount,
        double parseMs,
        long parseHeapDeltaBytes,
        double uploadImportMs,
        double downloadMs,
        double cacheMissMs,
        double cacheHitMs,
        double warmupColdMs,
        double warmupWarmMs,
        int warmupRows,
        double telemetryColdMs,
        double telemetryWarmMs,
        int telemetryPoints,
        int actionMarkers,
        int indicatorPoints,
        double stagedAppendMs
    ) {
    }

    private record TimedResult<T>(T value, double durationMs) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface GenericCheckedSupplier<T> {
        T get() throws Exception;
    }
}
