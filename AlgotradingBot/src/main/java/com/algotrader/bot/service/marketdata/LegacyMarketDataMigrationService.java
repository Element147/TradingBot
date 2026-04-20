package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleId;
import com.algotrader.bot.entity.MarketDataCandleSegment;
import com.algotrader.bot.entity.MarketDataSeries;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import com.algotrader.bot.service.HistoricalDataCsvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LegacyMarketDataMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(LegacyMarketDataMigrationService.class);
    private static final String LEGACY_PROVIDER_ID = "legacy-dataset";
    private static final String LEGACY_EXCHANGE_ID = "LEGACY";
    private static final Pattern PAIR_SPLITTER = Pattern.compile("[/_:-]");
    private static final List<String> QUOTE_ASSET_SUFFIXES = List.of("USDT", "USDC", "USD", "BTC", "ETH", "EUR");

    private final BacktestDatasetRepository backtestDatasetRepository;
    private final MarketDataSeriesRepository marketDataSeriesRepository;
    private final MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;
    private final MarketDataCandleRepository marketDataCandleRepository;
    private final HistoricalDataCsvParser historicalDataCsvParser;
    private final TransactionTemplate transactionTemplate;

    public LegacyMarketDataMigrationService(BacktestDatasetRepository backtestDatasetRepository,
                                            MarketDataSeriesRepository marketDataSeriesRepository,
                                            MarketDataCandleSegmentRepository marketDataCandleSegmentRepository,
                                            MarketDataCandleRepository marketDataCandleRepository,
                                            HistoricalDataCsvParser historicalDataCsvParser,
                                            PlatformTransactionManager transactionManager) {
        this.backtestDatasetRepository = backtestDatasetRepository;
        this.marketDataSeriesRepository = marketDataSeriesRepository;
        this.marketDataCandleSegmentRepository = marketDataCandleSegmentRepository;
        this.marketDataCandleRepository = marketDataCandleRepository;
        this.historicalDataCsvParser = historicalDataCsvParser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LegacyMarketDataMigrationSummary migrate(LegacyMarketDataMigrationRequest request) {
        List<BacktestDataset> datasets = selectDatasets(request);
        List<LegacyMarketDataMigrationDatasetResult> results = new ArrayList<>(datasets.size());
        for (BacktestDataset dataset : datasets) {
            LegacyMarketDataMigrationDatasetResult result;
            try {
                result = request.dryRun()
                    ? inspectDataset(dataset)
                    : Objects.requireNonNull(transactionTemplate.execute(status -> migrateDataset(dataset)));
            } catch (Exception exception) {
                logger.error("legacy_market_data_migration dataset_id={} status=FAILED message={}", dataset.getId(), exception.getMessage(), exception);
                result = new LegacyMarketDataMigrationDatasetResult(
                    dataset.getId(),
                    dataset.getName(),
                    dataset.getChecksumSha256(),
                    dataset.getRowCount(),
                    dataset.getSymbolsCsv(),
                    0,
                    0,
                    0,
                    0,
                    dataset.getRowCount() == null ? 0 : dataset.getRowCount(),
                    "FAILED",
                    exception.getMessage() == null ? "Migration failed." : exception.getMessage()
                );
            }
            logger.info(result.toLogLine());
            results.add(result);
        }

        LegacyMarketDataMigrationSummary summary = new LegacyMarketDataMigrationSummary(request.dryRun(), results);
        logger.info(summary.renderReport());
        return summary;
    }

    public LegacyMarketDataReconciliationSummary reconcile(LegacyMarketDataMigrationRequest request) {
        List<BacktestDataset> datasets = selectDatasets(request);
        List<LegacyMarketDataReconciliationDatasetResult> results = new ArrayList<>(datasets.size());
        for (BacktestDataset dataset : datasets) {
            LegacyMarketDataReconciliationDatasetResult result;
            try {
                result = reconcileDataset(dataset);
            } catch (Exception exception) {
                logger.error("legacy_market_data_reconciliation dataset_id={} status=FAILED message={}", dataset.getId(), exception.getMessage(), exception);
                result = failedReconciliationResult(
                    dataset,
                    List.of(exception.getMessage() == null ? "Reconciliation failed." : exception.getMessage())
                );
            }
            logger.info(result.toLogLine());
            results.add(result);
        }

        LegacyMarketDataReconciliationSummary summary = new LegacyMarketDataReconciliationSummary(results);
        logger.info(summary.renderReport());
        return summary;
    }

    public void ingestDataset(BacktestDataset dataset,
                              String sourceType,
                              String segmentNotes,
                              String providerBatchReference) {
        transactionTemplate.executeWithoutResult(status -> {
            List<SymbolMigrationPlan> plans = buildSymbolPlans(dataset);
            persistDataset(dataset, plans, sourceType, segmentNotes, providerBatchReference);
        });
    }

    private LegacyMarketDataMigrationDatasetResult inspectDataset(BacktestDataset dataset) {
        List<SymbolMigrationPlan> plans = buildSymbolPlans(dataset);
        int skippedSegments = 0;
        for (SymbolMigrationPlan plan : plans) {
            MarketDataSeries existingSeries = findExistingSeries(plan.descriptor());
            if (existingSeries != null && marketDataCandleSegmentRepository.findByDatasetSeriesTimeframeAndChecksum(
                dataset.getId(),
                existingSeries.getId(),
                plan.timeframe().id(),
                plan.segmentChecksumSha256()
            ).isPresent()) {
                skippedSegments++;
            }
        }

        return new LegacyMarketDataMigrationDatasetResult(
            dataset.getId(),
            dataset.getName(),
            dataset.getChecksumSha256(),
            dataset.getRowCount(),
            dataset.getSymbolsCsv(),
            plans.size(),
            Math.max(0, plans.size() - skippedSegments),
            plans.stream().mapToInt(plan -> plan.candles().size()).sum(),
            0,
            0,
            "DRY_RUN_READY",
            "Dry run inspected " + plans.size() + " symbol or timeframe groups."
        );
    }

    private LegacyMarketDataMigrationDatasetResult migrateDataset(BacktestDataset dataset) {
        List<SymbolMigrationPlan> plans = buildSymbolPlans(dataset);
        DatasetWriteSummary writeSummary = persistDataset(
            dataset,
            plans,
            "LEGACY_DATASET",
            "Migrated from legacy dataset CSV storage.",
            dataset.getChecksumSha256()
        );

        String status = writeSummary.migratedSegmentCount() > 0 ? "MIGRATED" : "SKIPPED_ALREADY_MIGRATED";
        String message = writeSummary.migratedSegmentCount() > 0
            ? "Migrated " + writeSummary.migratedSegmentCount() + " segments from legacy CSV."
            : "All matching legacy segments were already present or covered by identical candles.";
        return new LegacyMarketDataMigrationDatasetResult(
            dataset.getId(),
            dataset.getName(),
            dataset.getChecksumSha256(),
            dataset.getRowCount(),
            dataset.getSymbolsCsv(),
            plans.size(),
            writeSummary.migratedSegmentCount(),
            writeSummary.insertedCandleCount(),
            writeSummary.duplicateCandleCount(),
            0,
            status,
            message
        );
    }

    private DatasetWriteSummary persistDataset(BacktestDataset dataset,
                                               List<SymbolMigrationPlan> plans,
                                               String sourceType,
                                               String segmentNotes,
                                               String providerBatchReference) {
        int migratedSeriesCount = 0;
        int migratedSegmentCount = 0;
        int insertedCandleCount = 0;
        int duplicateCandleCount = 0;

        for (SymbolMigrationPlan plan : plans) {
            MarketDataSeries series = findExistingSeries(plan.descriptor());
            if (series == null) {
                series = marketDataSeriesRepository.save(plan.descriptor().toSeriesEntity());
                migratedSeriesCount++;
            }

            if (marketDataCandleSegmentRepository.findByDatasetSeriesTimeframeAndChecksum(
                dataset.getId(),
                series.getId(),
                plan.timeframe().id(),
                plan.segmentChecksumSha256()
            ).isPresent()) {
                continue;
            }

            List<MarketDataCandle> existingCandles = marketDataCandleRepository.findCandlesInRange(
                series.getId(),
                plan.timeframe().id(),
                plan.coverageStart(),
                plan.coverageEnd()
            );
            Map<LocalDateTime, MarketDataCandle> existingByBucket = existingCandles.stream()
                .collect(Collectors.toMap(candle -> candle.getId().getBucketStart(), candle -> candle, (left, _right) -> left, LinkedHashMap::new));

            List<OHLCVData> candlesToInsert = new ArrayList<>(plan.candles().size());
            int duplicatesForPlan = 0;
            for (OHLCVData candle : plan.candles()) {
                MarketDataCandle existing = existingByBucket.get(candle.getTimestamp());
                if (existing == null) {
                    candlesToInsert.add(candle);
                    continue;
                }
                if (matches(existing, candle)) {
                    duplicatesForPlan++;
                    continue;
                }
                throw new IllegalStateException(
                    "Conflicting candle already exists for dataset " + dataset.getId()
                        + ", symbol " + plan.symbolDisplay()
                        + ", timeframe " + plan.timeframe().id()
                        + ", bucket " + candle.getTimestamp()
                );
            }

            if (candlesToInsert.isEmpty()) {
                duplicateCandleCount += duplicatesForPlan;
                continue;
            }

            MarketDataCandleSegment segment = marketDataCandleSegmentRepository.save(
                plan.toSegmentEntity(dataset, series, sourceType, segmentNotes, providerBatchReference)
            );
            MarketDataSeries persistedSeries = series;
            marketDataCandleRepository.saveAll(
                candlesToInsert.stream()
                    .map(candle -> toMarketDataCandle(persistedSeries, segment, candle))
                    .toList()
            );
            migratedSegmentCount++;
            insertedCandleCount += candlesToInsert.size();
            duplicateCandleCount += duplicatesForPlan;
        }

        return new DatasetWriteSummary(
            migratedSeriesCount,
            migratedSegmentCount,
            insertedCandleCount,
            duplicateCandleCount
        );
    }

    private LegacyMarketDataReconciliationDatasetResult reconcileDataset(BacktestDataset dataset) {
        List<SymbolMigrationPlan> plans = buildSymbolPlans(dataset);
        List<MarketDataCandleSegment> datasetSegments = marketDataCandleSegmentRepository.findByDatasetIdWithSeries(dataset.getId());
        Map<SeriesTimeframeKey, List<MarketDataCandleSegment>> actualSegmentsByKey = datasetSegments.stream()
            .collect(Collectors.groupingBy(
                segment -> new SeriesTimeframeKey(segment.getSeries().getId(), segment.getTimeframe()),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        Set<Long> actualSeriesIds = datasetSegments.stream()
            .map(segment -> segment.getSeries().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualSymbols = datasetSegments.stream()
            .map(segment -> segment.getSeries().getSymbolDisplay())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> discrepancies = new ArrayList<>();
        List<String> expectedDigests = new ArrayList<>(plans.size());
        Set<SeriesTimeframeKey> expectedKeys = new LinkedHashSet<>(plans.size());
        int expectedCandleCount = 0;
        int actualCandleCount = 0;
        LocalDateTime actualCoverageStart = null;
        LocalDateTime actualCoverageEnd = null;

        for (SymbolMigrationPlan plan : plans) {
            expectedKeys.add(new SeriesTimeframeKey(plan.descriptor().identityKey(), plan.timeframe().id()));
            expectedCandleCount += plan.candles().size();
            expectedDigests.add(planDigest(
                plan.symbolDisplay(),
                plan.timeframe().id(),
                plan.segmentChecksumSha256(),
                plan.candles().size(),
                plan.coverageStart(),
                plan.coverageEnd()
            ));

            MarketDataSeries series = findExistingSeries(plan.descriptor());
            if (series == null) {
                discrepancies.add("Missing normalized series for symbol " + plan.symbolDisplay()
                    + " (provider=" + plan.descriptor().providerId()
                    + ", exchange=" + plan.descriptor().exchangeId()
                    + ", assetClass=" + plan.descriptor().assetClass() + ").");
                continue;
            }

            SeriesTimeframeKey actualKey = new SeriesTimeframeKey(series.getId(), plan.timeframe().id());
            List<MarketDataCandleSegment> matchingSegments = actualSegmentsByKey.getOrDefault(actualKey, List.of());
            if (matchingSegments.isEmpty()) {
                discrepancies.add("Missing normalized segment for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id() + ".");
                continue;
            }

            List<MarketDataCandle> actualCandles = marketDataCandleRepository.findDatasetSeriesCandlesInRange(
                dataset.getId(),
                series.getId(),
                plan.timeframe().id(),
                plan.coverageStart(),
                plan.coverageEnd()
            );
            if (actualCandles.isEmpty()) {
                discrepancies.add("Normalized store has segment metadata but no candles for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id() + ".");
                continue;
            }

            actualCandleCount += actualCandles.size();
            LocalDateTime actualStart = actualCandles.getFirst().getId().getBucketStart();
            LocalDateTime actualEnd = actualCandles.getLast().getId().getBucketStart();
            if (actualCoverageStart == null || actualStart.isBefore(actualCoverageStart)) {
                actualCoverageStart = actualStart;
            }
            if (actualCoverageEnd == null || actualEnd.isAfter(actualCoverageEnd)) {
                actualCoverageEnd = actualEnd;
            }

            if (actualCandles.size() != plan.candles().size()) {
                discrepancies.add("Row count mismatch for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id()
                    + ": expected " + plan.candles().size()
                    + " but found " + actualCandles.size() + " normalized candles.");
            }
            if (!actualStart.equals(plan.coverageStart())) {
                discrepancies.add("Coverage start mismatch for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id()
                    + ": expected " + plan.coverageStart()
                    + " but found " + actualStart + ".");
            }
            if (!actualEnd.equals(plan.coverageEnd())) {
                discrepancies.add("Coverage end mismatch for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id()
                    + ": expected " + plan.coverageEnd()
                    + " but found " + actualEnd + ".");
            }

            String actualChecksum = checksumForMarketDataCandles(actualCandles);
            if (!actualChecksum.equals(plan.segmentChecksumSha256())) {
                discrepancies.add("Checksum mismatch for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id()
                    + ": expected " + plan.segmentChecksumSha256()
                    + " but found " + actualChecksum + ".");
            }
            boolean matchingSegmentChecksum = matchingSegments.stream()
                .map(MarketDataCandleSegment::getChecksumSha256)
                .anyMatch(plan.segmentChecksumSha256()::equals);
            if (!matchingSegmentChecksum) {
                String segmentChecksums = matchingSegments.stream()
                    .map(MarketDataCandleSegment::getChecksumSha256)
                    .collect(Collectors.joining(","));
                discrepancies.add("Segment checksum mismatch for dataset " + dataset.getId()
                    + ", symbol " + plan.symbolDisplay()
                    + ", timeframe " + plan.timeframe().id()
                    + ": expected segment checksum " + plan.segmentChecksumSha256()
                    + " but found [" + segmentChecksums + "].");
            }
        }

        Set<String> expectedSymbols = plans.stream()
            .map(SymbolMigrationPlan::symbolDisplay)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!actualSymbols.equals(expectedSymbols)) {
            discrepancies.add("Symbol set mismatch for dataset " + dataset.getId()
                + ": expected " + String.join(",", expectedSymbols)
                + " but found " + String.join(",", actualSymbols) + ".");
        }

        Set<SeriesTimeframeKey> actualIdentityKeys = datasetSegments.stream()
            .map(segment -> new SeriesTimeframeKey(marketDataSeriesIdentityKey(segment.getSeries()), segment.getTimeframe()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<SeriesTimeframeKey> unexpectedKeys = new LinkedHashSet<>(actualIdentityKeys);
        unexpectedKeys.removeAll(expectedKeys);
        if (!unexpectedKeys.isEmpty()) {
            discrepancies.add("Unexpected normalized segment identities for dataset " + dataset.getId()
                + ": " + unexpectedKeys.stream().map(SeriesTimeframeKey::render).collect(Collectors.joining(",")) + ".");
        }

        String expectedCoverageStart = plans.stream()
            .map(SymbolMigrationPlan::coverageStart)
            .min(LocalDateTime::compareTo)
            .map(LocalDateTime::toString)
            .orElse(null);
        String expectedCoverageEnd = plans.stream()
            .map(SymbolMigrationPlan::coverageEnd)
            .max(LocalDateTime::compareTo)
            .map(LocalDateTime::toString)
            .orElse(null);
        String actualCoverageStartText = actualCoverageStart == null ? null : actualCoverageStart.toString();
        String actualCoverageEndText = actualCoverageEnd == null ? null : actualCoverageEnd.toString();
        if (!Objects.equals(expectedCoverageStart, actualCoverageStartText)) {
            discrepancies.add("Dataset coverage start mismatch for dataset " + dataset.getId()
                + ": expected " + expectedCoverageStart + " but found " + actualCoverageStartText + ".");
        }
        if (!Objects.equals(expectedCoverageEnd, actualCoverageEndText)) {
            discrepancies.add("Dataset coverage end mismatch for dataset " + dataset.getId()
                + ": expected " + expectedCoverageEnd + " but found " + actualCoverageEndText + ".");
        }
        if (expectedCandleCount != actualCandleCount) {
            discrepancies.add("Dataset row count mismatch for dataset " + dataset.getId()
                + ": expected " + expectedCandleCount + " but found " + actualCandleCount + " normalized candles.");
        }

        String expectedHashSummary = checksumForStrings(expectedDigests);
        String actualHashSummary = checksumForStrings(buildActualDigests(datasetSegments));
        if (!expectedHashSummary.equals(actualHashSummary)) {
            discrepancies.add("Derived hash summary mismatch for dataset " + dataset.getId()
                + ": expected " + expectedHashSummary + " but found " + actualHashSummary + ".");
        }

        String status = discrepancies.isEmpty() ? "RECONCILED" : "FAILED";
        String rollbackAction = discrepancies.isEmpty()
            ? "No rollback required."
            : "Keep this dataset on the legacy CSV compatibility path, do not retire csv_data, and rerun migration after resolving the reported discrepancies.";
        return new LegacyMarketDataReconciliationDatasetResult(
            dataset.getId(),
            dataset.getName(),
            dataset.getChecksumSha256(),
            dataset.getRowCount(),
            dataset.getSymbolsCsv(),
            plans.size(),
            actualSeriesIds.size(),
            expectedCandleCount,
            actualCandleCount,
            expectedCoverageStart,
            actualCoverageStartText,
            expectedCoverageEnd,
            actualCoverageEndText,
            expectedHashSummary,
            actualHashSummary,
            status,
            rollbackAction,
            discrepancies
        );
    }

    private List<BacktestDataset> selectDatasets(LegacyMarketDataMigrationRequest request) {
        List<BacktestDataset> datasets = backtestDatasetRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        if (!request.datasetIds().isEmpty()) {
            Set<Long> requestedIds = request.datasetIds();
            datasets = datasets.stream()
                .filter(dataset -> requestedIds.contains(dataset.getId()))
                .toList();
        }
        if (request.limit() != null && request.limit() > 0 && datasets.size() > request.limit()) {
            return datasets.subList(0, request.limit());
        }
        return datasets;
    }

    private List<SymbolMigrationPlan> buildSymbolPlans(BacktestDataset dataset) {
        List<OHLCVData> candles = historicalDataCsvParser.parse(dataset.getCsvData()).stream()
            .sorted(Comparator.comparing(OHLCVData::getSymbol).thenComparing(OHLCVData::getTimestamp))
            .toList();
        Map<String, List<OHLCVData>> candlesBySymbol = candles.stream()
            .collect(Collectors.groupingBy(
                candle -> candle.getSymbol().trim(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        List<SymbolMigrationPlan> plans = new ArrayList<>(candlesBySymbol.size());
        for (Map.Entry<String, List<OHLCVData>> entry : candlesBySymbol.entrySet()) {
            List<OHLCVData> symbolCandles = entry.getValue().stream()
                .sorted(Comparator.comparing(OHLCVData::getTimestamp))
                .toList();
            MarketDataTimeframe timeframe = inferTimeframe(symbolCandles);
            LegacySeriesDescriptor descriptor = inferSeriesDescriptor(entry.getKey());
            plans.add(new SymbolMigrationPlan(
                descriptor,
                descriptor.symbolDisplay(),
                timeframe,
                symbolCandles,
                symbolCandles.getFirst().getTimestamp(),
                symbolCandles.getLast().getTimestamp(),
                checksumForCandles(symbolCandles)
            ));
        }
        return plans;
    }

    private MarketDataTimeframe inferTimeframe(List<OHLCVData> candles) {
        Duration minimumGap = null;
        for (int index = 1; index < candles.size(); index++) {
            Duration gap = Duration.between(candles.get(index - 1).getTimestamp(), candles.get(index).getTimestamp());
            if (gap.isNegative() || gap.isZero()) {
                continue;
            }
            if (minimumGap == null || gap.compareTo(minimumGap) < 0) {
                minimumGap = gap;
            }
        }
        if (minimumGap == null) {
            throw new IllegalStateException("Unable to infer timeframe from fewer than two ordered candles.");
        }
        return MarketDataTimeframe.fromStep(minimumGap);
    }

    private LegacySeriesDescriptor inferSeriesDescriptor(String rawSymbol) {
        String normalizedInput = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        if (normalizedInput.isBlank()) {
            throw new IllegalStateException("Legacy dataset contains a blank symbol.");
        }

        String[] explicitTokens = PAIR_SPLITTER.split(normalizedInput);
        if (explicitTokens.length == 2 && !explicitTokens[0].isBlank() && !explicitTokens[1].isBlank()) {
            return cryptoDescriptor(explicitTokens[0], explicitTokens[1]);
        }

        for (String quoteAsset : QUOTE_ASSET_SUFFIXES) {
            if (normalizedInput.endsWith(quoteAsset) && normalizedInput.length() > quoteAsset.length()) {
                String baseAsset = normalizedInput.substring(0, normalizedInput.length() - quoteAsset.length());
                if (baseAsset.length() >= 2) {
                    return cryptoDescriptor(baseAsset, quoteAsset);
                }
            }
        }

        return new LegacySeriesDescriptor(
            LEGACY_PROVIDER_ID,
            "",
            LEGACY_EXCHANGE_ID,
            "DATASET",
            "EQUITY",
            "SPOT",
            normalizedInput.replaceAll("[^A-Z0-9.]", ""),
            normalizedInput,
            "",
            "USD",
            "USD",
            "US",
            "America/New_York",
            "US_EQUITIES"
        );
    }

    private LegacySeriesDescriptor cryptoDescriptor(String baseAsset, String quoteAsset) {
        String normalizedBase = baseAsset.trim().toUpperCase(Locale.ROOT);
        String normalizedQuote = quoteAsset.trim().toUpperCase(Locale.ROOT);
        return new LegacySeriesDescriptor(
            LEGACY_PROVIDER_ID,
            "",
            LEGACY_EXCHANGE_ID,
            "DATASET",
            "CRYPTO_SPOT",
            "SPOT",
            normalizedBase + normalizedQuote,
            normalizedBase + "/" + normalizedQuote,
            normalizedBase,
            normalizedQuote,
            normalizedQuote,
            "",
            "UTC",
            "ALWAYS_ON"
        );
    }

    private MarketDataSeries findExistingSeries(LegacySeriesDescriptor descriptor) {
        return marketDataSeriesRepository.findByProviderIdAndExchangeIdAndSymbolNormalizedAndAssetClass(
                descriptor.providerId(),
                descriptor.exchangeId(),
                descriptor.symbolNormalized(),
                descriptor.assetClass()
            )
            .orElse(null);
    }

    private List<String> buildActualDigests(List<MarketDataCandleSegment> datasetSegments) {
        Map<SeriesTimeframeKey, List<MarketDataCandleSegment>> segmentsByIdentity = datasetSegments.stream()
            .collect(Collectors.groupingBy(
                segment -> new SeriesTimeframeKey(marketDataSeriesIdentityKey(segment.getSeries()), segment.getTimeframe()),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        return segmentsByIdentity.values().stream()
            .map(segments -> {
                MarketDataCandleSegment firstSegment = segments.getFirst();
                LocalDateTime coverageStart = segments.stream()
                    .map(MarketDataCandleSegment::getCoverageStart)
                    .min(LocalDateTime::compareTo)
                    .orElseThrow();
                LocalDateTime coverageEnd = segments.stream()
                    .map(MarketDataCandleSegment::getCoverageEnd)
                    .max(LocalDateTime::compareTo)
                    .orElseThrow();
                List<MarketDataCandle> candles = marketDataCandleRepository.findDatasetSeriesCandlesInRange(
                    firstSegment.getDataset().getId(),
                    firstSegment.getSeries().getId(),
                    firstSegment.getTimeframe(),
                    coverageStart,
                    coverageEnd
                );
                LocalDateTime actualStart = candles.isEmpty() ? null : candles.getFirst().getId().getBucketStart();
                LocalDateTime actualEnd = candles.isEmpty() ? null : candles.getLast().getId().getBucketStart();
                return planDigest(
                    firstSegment.getSeries().getSymbolDisplay(),
                    firstSegment.getTimeframe(),
                    checksumForMarketDataCandles(candles),
                    candles.size(),
                    actualStart,
                    actualEnd
                );
            })
            .sorted()
            .toList();
    }

    private String marketDataSeriesIdentityKey(MarketDataSeries series) {
        return series.getProviderId() + "|" + series.getExchangeId() + "|" + series.getAssetClass() + "|" + series.getSymbolNormalized();
    }

    private boolean matches(MarketDataCandle existing, OHLCVData candle) {
        return existing.getOpenPrice().compareTo(candle.getOpen()) == 0
            && existing.getHighPrice().compareTo(candle.getHigh()) == 0
            && existing.getLowPrice().compareTo(candle.getLow()) == 0
            && existing.getClosePrice().compareTo(candle.getClose()) == 0
            && existing.getVolume().compareTo(candle.getVolume()) == 0;
    }

    private MarketDataCandle toMarketDataCandle(MarketDataSeries series,
                                                MarketDataCandleSegment segment,
                                                OHLCVData candle) {
        MarketDataCandle marketDataCandle = new MarketDataCandle();
        marketDataCandle.setId(new MarketDataCandleId(series.getId(), segment.getTimeframe(), candle.getTimestamp()));
        marketDataCandle.setSeries(series);
        marketDataCandle.setSegment(segment);
        marketDataCandle.setOpenPrice(candle.getOpen());
        marketDataCandle.setHighPrice(candle.getHigh());
        marketDataCandle.setLowPrice(candle.getLow());
        marketDataCandle.setClosePrice(candle.getClose());
        marketDataCandle.setVolume(candle.getVolume());
        marketDataCandle.setTradeCount(0L);
        marketDataCandle.setVwap(candle.getClose());
        return marketDataCandle;
    }

    private String checksumForCandles(List<OHLCVData> candles) {
        return checksumForStrings(candles.stream()
            .map(candle -> candle.getTimestamp() + "|" + candle.getSymbol() + "|" + candle.getOpen() + "|"
                + candle.getHigh() + "|" + candle.getLow() + "|" + candle.getClose() + "|" + candle.getVolume())
            .toList());
    }

    private String checksumForMarketDataCandles(List<MarketDataCandle> candles) {
        return checksumForStrings(candles.stream()
            .map(candle -> candle.getId().getBucketStart() + "|" + candle.getSeries().getSymbolDisplay() + "|"
                + candle.getOpenPrice() + "|" + candle.getHighPrice() + "|" + candle.getLowPrice() + "|"
                + candle.getClosePrice() + "|" + candle.getVolume())
            .toList());
    }

    private String checksumForStrings(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines.stream().sorted().toList()) {
                digest.update((line + "\n").getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String planDigest(String symbol,
                              String timeframe,
                              String checksum,
                              int rowCount,
                              LocalDateTime coverageStart,
                              LocalDateTime coverageEnd) {
        return symbol + "|" + timeframe + "|" + rowCount + "|" + coverageStart + "|" + coverageEnd + "|" + checksum;
    }

    private LegacyMarketDataReconciliationDatasetResult failedReconciliationResult(BacktestDataset dataset, List<String> discrepancies) {
        return new LegacyMarketDataReconciliationDatasetResult(
            dataset.getId(),
            dataset.getName(),
            dataset.getChecksumSha256(),
            dataset.getRowCount(),
            dataset.getSymbolsCsv(),
            0,
            0,
            dataset.getRowCount(),
            0,
            dataset.getDataStart() == null ? null : dataset.getDataStart().toString(),
            null,
            dataset.getDataEnd() == null ? null : dataset.getDataEnd().toString(),
            null,
            checksumForStrings(List.of(dataset.getChecksumSha256(), String.valueOf(dataset.getRowCount()), dataset.getSymbolsCsv())),
            "",
            "FAILED",
            "Keep this dataset on the legacy CSV compatibility path, do not retire csv_data, and rerun migration after resolving the reported discrepancies.",
            discrepancies
        );
    }

    private record SymbolMigrationPlan(LegacySeriesDescriptor descriptor,
                                       String symbolDisplay,
                                       MarketDataTimeframe timeframe,
                                       List<OHLCVData> candles,
                                       LocalDateTime coverageStart,
                                       LocalDateTime coverageEnd,
                                       String segmentChecksumSha256) {

        private MarketDataCandleSegment toSegmentEntity(BacktestDataset dataset,
                                                        MarketDataSeries series,
                                                        String sourceType,
                                                        String notes,
                                                        String providerBatchReference) {
            MarketDataCandleSegment segment = new MarketDataCandleSegment();
            segment.setDataset(dataset);
            segment.setSeries(series);
            segment.setTimeframe(timeframe.id());
            segment.setSourceType(sourceType);
            segment.setCoverageStart(coverageStart);
            segment.setCoverageEnd(coverageEnd);
            segment.setRowCount(candles.size());
            segment.setChecksumSha256(segmentChecksumSha256);
            segment.setSchemaVersion("ohlcv-v1");
            segment.setResolutionTier("EXACT_RAW");
            segment.setSourcePriority((short) 100);
            segment.setSegmentStatus("ACTIVE");
            segment.setStorageEncoding("ROW_STORE");
            segment.setArchived(Boolean.FALSE);
            segment.setProviderBatchReference(providerBatchReference);
            segment.setNotes(notes);
            segment.setLineageJson(
                "{\"migration\":\"legacy-dataset\",\"datasetId\":" + dataset.getId()
                    + ",\"timeframe\":\"" + timeframe.id() + "\",\"symbol\":\"" + descriptor.symbolDisplay() + "\"}"
            );
            return segment;
        }
    }

    private record DatasetWriteSummary(int migratedSeriesCount,
                                       int migratedSegmentCount,
                                       int insertedCandleCount,
                                       int duplicateCandleCount) {
    }

    private record SeriesTimeframeKey(String seriesIdentity, String timeframe) {

        private SeriesTimeframeKey(Long seriesId, String timeframe) {
            this(String.valueOf(seriesId), timeframe);
        }

        private String render() {
            return seriesIdentity + "@" + timeframe;
        }
    }

    private record LegacySeriesDescriptor(String providerId,
                                          String brokerId,
                                          String exchangeId,
                                          String venueType,
                                          String assetClass,
                                          String instrumentType,
                                          String symbolNormalized,
                                          String symbolDisplay,
                                          String baseAsset,
                                          String quoteAsset,
                                          String currencyCode,
                                          String countryCode,
                                          String timezoneName,
                                          String sessionTemplate) {

        private String identityKey() {
            return providerId + "|" + exchangeId + "|" + assetClass + "|" + symbolNormalized;
        }

        private MarketDataSeries toSeriesEntity() {
            MarketDataSeries series = new MarketDataSeries();
            series.setProviderId(providerId);
            series.setBrokerId(brokerId);
            series.setExchangeId(exchangeId);
            series.setVenueType(venueType);
            series.setAssetClass(assetClass);
            series.setInstrumentType(instrumentType);
            series.setSymbolNormalized(symbolNormalized);
            series.setSymbolDisplay(symbolDisplay);
            series.setBaseAsset(baseAsset);
            series.setQuoteAsset(quoteAsset);
            series.setCurrencyCode(currencyCode);
            series.setCountryCode(countryCode);
            series.setTimezoneName(timezoneName);
            series.setSessionTemplate(sessionTemplate);
            series.setProviderMetadataJson("{\"source\":\"legacy-dataset-migration\"}");
            return series;
        }
    }
}
