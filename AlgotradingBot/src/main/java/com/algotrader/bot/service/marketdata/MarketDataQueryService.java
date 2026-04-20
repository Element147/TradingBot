package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleSegment;
import com.algotrader.bot.entity.MarketDataSeries;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.service.BacktestDatasetCandleCache;
import com.algotrader.bot.service.BacktestDatasetStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MarketDataQueryService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataQueryService.class);
    private static final long SLOW_QUERY_THRESHOLD_MILLIS = 500L;

    private final MarketDataCandleRepository marketDataCandleRepository;
    private final BacktestDatasetStorageService backtestDatasetStorageService;
    private final BacktestDatasetCandleCache backtestDatasetCandleCache;
    private final MarketDataResampler marketDataResampler;
    private final MarketDataQueryMetrics marketDataQueryMetrics;

    public MarketDataQueryService(MarketDataCandleRepository marketDataCandleRepository,
                                  BacktestDatasetStorageService backtestDatasetStorageService,
                                  BacktestDatasetCandleCache backtestDatasetCandleCache,
                                  MarketDataResampler marketDataResampler,
                                  MarketDataQueryMetrics marketDataQueryMetrics) {
        this.marketDataCandleRepository = marketDataCandleRepository;
        this.backtestDatasetStorageService = backtestDatasetStorageService;
        this.backtestDatasetCandleCache = backtestDatasetCandleCache;
        this.marketDataResampler = marketDataResampler;
        this.marketDataQueryMetrics = marketDataQueryMetrics;
    }

    public List<MarketDataQueriedCandle> loadCandlesForSeries(Long seriesId,
                                                              String timeframe,
                                                              LocalDateTime windowStart,
                                                              LocalDateTime windowEnd) {
        return queryCandlesForSeries(seriesId, timeframe, windowStart, windowEnd, MarketDataQueryMode.BEST_AVAILABLE).candles();
    }

    public MarketDataQueryResult queryCandlesForSeries(Long seriesId,
                                                       String timeframe,
                                                       LocalDateTime windowStart,
                                                       LocalDateTime windowEnd,
                                                       MarketDataQueryMode queryMode) {
        long startedAt = System.nanoTime();
        List<MarketDataQueriedCandle> exactCandles = marketDataCandleRepository.findCandlesInRange(seriesId, timeframe, windowStart, windowEnd).stream()
            .map(this::toQueriedCandle)
            .sorted(Comparator.comparing(MarketDataQueriedCandle::timestamp).thenComparing(MarketDataQueriedCandle::symbol))
            .toList();
        MarketDataQueryResult result = new MarketDataQueryResult(
            exactCandles,
            buildGaps(exactCandles, timeframe, windowStart, windowEnd, Set.of()),
            timeframe,
            queryMode
        );
        recordQueryObservation(
            "series",
            "series_id=" + seriesId,
            timeframe,
            queryMode,
            windowStart,
            windowEnd,
            Set.of(),
            Set.of(),
            false,
            startedAt,
            result
        );
        return result;
    }

    public List<MarketDataQueriedCandle> loadCandlesForDataset(Long datasetId,
                                                               String timeframe,
                                                               LocalDateTime windowStart,
                                                               LocalDateTime windowEnd,
                                                               Set<String> requestedSymbols) {
        return queryCandlesForDataset(
            datasetId,
            timeframe,
            windowStart,
            windowEnd,
            requestedSymbols,
            MarketDataQueryMode.BEST_AVAILABLE
        ).candles();
    }

    public MarketDataQueryResult queryCandlesForDataset(Long datasetId,
                                                        String timeframe,
                                                        LocalDateTime windowStart,
                                                        LocalDateTime windowEnd,
                                                        Set<String> requestedSymbols,
                                                        MarketDataQueryMode queryMode) {
        long startedAt = System.nanoTime();
        Set<String> normalizedSymbols = normalizeRequestedSymbols(requestedSymbols);
        List<MarketDataQueriedCandle> exactCandles = loadExactDatasetCandles(datasetId, timeframe, windowStart, windowEnd, normalizedSymbols);
        List<MarketDataQueriedCandle> mergedCandles = exactCandles;
        String sourceTimeframe = timeframe;
        Set<String> rollupSourceTimeframes = new LinkedHashSet<>();

        boolean relationalSourceSeen = !exactCandles.isEmpty();
        if (queryMode.allowsRollup()) {
            RollupAttemptResult rollupAttempt = rollUpFromFinerTimeframe(
                datasetId,
                timeframe,
                windowStart,
                windowEnd,
                normalizedSymbols
            );
            List<MarketDataQueriedCandle> rolledCandles = rollupAttempt.candles();
            rollupSourceTimeframes.addAll(rollupAttempt.sourceTimeframes());
            relationalSourceSeen = relationalSourceSeen || rollupAttempt.sourceSeen();
            if (!rolledCandles.isEmpty()) {
                mergedCandles = mergeCandles(exactCandles, rolledCandles);
                if (exactCandles.isEmpty()) {
                    sourceTimeframe = rolledCandles.stream()
                        .map(candle -> candle.provenance() == null ? null : candle.provenance().timeframe())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(timeframe);
                }
            }
        }

        List<MarketDataQueryGap> gaps = buildGaps(mergedCandles, timeframe, windowStart, windowEnd, normalizedSymbols);
        if (!mergedCandles.isEmpty() || relationalSourceSeen) {
            MarketDataQueryResult result = new MarketDataQueryResult(mergedCandles, gaps, sourceTimeframe, queryMode);
            recordQueryObservation(
                "dataset",
                "dataset_id=" + datasetId,
                timeframe,
                queryMode,
                windowStart,
                windowEnd,
                normalizedSymbols,
                rollupSourceTimeframes,
                false,
                startedAt,
                result
            );
            return result;
        }

        logger.info(
            "market_data_query event=legacy_fallback dataset_id={} timeframe={} query_mode={} symbols={} window_start={} window_end={} reason=no_relational_source",
            datasetId,
            timeframe,
            queryMode,
            normalizedSymbols.isEmpty() ? "<all>" : normalizedSymbols,
            windowStart,
            windowEnd
        );
        List<MarketDataQueriedCandle> legacyCandles = loadLegacyCandles(datasetId, timeframe, windowStart, windowEnd, normalizedSymbols);
        MarketDataQueryResult result = new MarketDataQueryResult(
            legacyCandles,
            buildGaps(legacyCandles, timeframe, windowStart, windowEnd, normalizedSymbols),
            timeframe,
            queryMode
        );
        recordQueryObservation(
            "dataset",
            "dataset_id=" + datasetId,
            timeframe,
            queryMode,
            windowStart,
            windowEnd,
            normalizedSymbols,
            rollupSourceTimeframes,
            true,
            startedAt,
            result
        );
        return result;
    }

    private List<MarketDataQueriedCandle> loadExactDatasetCandles(Long datasetId,
                                                                  String timeframe,
                                                                  LocalDateTime windowStart,
                                                                  LocalDateTime windowEnd,
                                                                  Set<String> normalizedSymbols) {
        return normalizedSymbols.isEmpty()
            ? marketDataCandleRepository.findDatasetCandlesInRange(datasetId, timeframe, windowStart, windowEnd).stream()
                .map(this::toQueriedCandle)
                .sorted(Comparator.comparing(MarketDataQueriedCandle::timestamp).thenComparing(MarketDataQueriedCandle::symbol))
                .toList()
            : normalizedSymbols.stream()
                .flatMap(symbol -> marketDataCandleRepository
                    .findDatasetCandlesForSymbolInRange(datasetId, timeframe, symbol, windowStart, windowEnd)
                    .stream())
                .map(this::toQueriedCandle)
                .sorted(Comparator.comparing(MarketDataQueriedCandle::timestamp).thenComparing(MarketDataQueriedCandle::symbol))
                .toList();
    }

    private RollupAttemptResult rollUpFromFinerTimeframe(Long datasetId,
                                                         String timeframe,
                                                         LocalDateTime windowStart,
                                                         LocalDateTime windowEnd,
                                                         Set<String> normalizedSymbols) {
        MarketDataTimeframe requestedTimeframe = MarketDataTimeframe.from(timeframe);
        Map<String, MarketDataQueriedCandle> merged = new LinkedHashMap<>();
        boolean sourceSeen = false;
        Set<String> sourceTimeframes = new LinkedHashSet<>();
        for (MarketDataTimeframe candidate : finerTimeframesFor(requestedTimeframe)) {
            List<MarketDataQueriedCandle> sourceCandles = loadExactDatasetCandles(
                datasetId,
                candidate.id(),
                windowStart,
                windowEnd,
                normalizedSymbols
            );
            if (sourceCandles.isEmpty()) {
                continue;
            }
            sourceSeen = true;
            sourceTimeframes.add(candidate.id());

            List<MarketDataQueriedCandle> rolledCandles = marketDataResampler.resampleQueriedCandles(
                sourceCandles,
                candidate.id(),
                timeframe
            );
            if (!rolledCandles.isEmpty()) {
                rolledCandles.forEach(candle -> merged.putIfAbsent(bucketKey(candle.symbol(), candle.timestamp()), candle));
                logger.info(
                    "market_data_query event=rollup dataset_id={} source_timeframe={} target_timeframe={} source_candles={} rolled_candles={} window_start={} window_end={}",
                    datasetId,
                    candidate.id(),
                    timeframe,
                    sourceCandles.size(),
                    rolledCandles.size(),
                    windowStart,
                    windowEnd
                );
            }
        }
        return new RollupAttemptResult(
            merged.values().stream()
                .sorted(Comparator.comparing(MarketDataQueriedCandle::timestamp).thenComparing(MarketDataQueriedCandle::symbol))
                .toList(),
            sourceSeen,
            sourceTimeframes
        );
    }

    private List<MarketDataQueriedCandle> loadLegacyCandles(Long datasetId,
                                                            String timeframe,
                                                            LocalDateTime windowStart,
                                                            LocalDateTime windowEnd,
                                                            Set<String> requestedSymbols) {
        BacktestDataset dataset = backtestDatasetStorageService.getDataset(datasetId);
        List<OHLCVData> filtered = backtestDatasetCandleCache.getOrParse(dataset).stream()
            .filter(candle -> !candle.getTimestamp().isBefore(windowStart))
            .filter(candle -> !candle.getTimestamp().isAfter(windowEnd))
            .filter(candle -> requestedSymbols.isEmpty() || requestedSymbols.contains(candle.getSymbol().toUpperCase(Locale.ROOT)))
            .sorted(Comparator.comparing(OHLCVData::getSymbol).thenComparing(OHLCVData::getTimestamp))
            .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }

        return marketDataResampler.resample(filtered, timeframe).stream()
            .map(candle -> new MarketDataQueriedCandle(
                candle.getTimestamp(),
                candle.getSymbol(),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume(),
                MarketDataCandleProvenance.legacy(datasetId, candle.getSymbol(), timeframe)
            ))
            .sorted(Comparator.comparing(MarketDataQueriedCandle::timestamp).thenComparing(MarketDataQueriedCandle::symbol))
            .toList();
    }

    private List<MarketDataQueriedCandle> mergeCandles(List<MarketDataQueriedCandle> exactCandles,
                                                       List<MarketDataQueriedCandle> rolledCandles) {
        Map<String, MarketDataQueriedCandle> merged = new LinkedHashMap<>();
        rolledCandles.forEach(candle -> merged.put(bucketKey(candle.symbol(), candle.timestamp()), candle));
        exactCandles.forEach(candle -> merged.put(bucketKey(candle.symbol(), candle.timestamp()), candle));
        return merged.values().stream()
            .sorted(Comparator.comparing(MarketDataQueriedCandle::timestamp).thenComparing(MarketDataQueriedCandle::symbol))
            .toList();
    }

    private List<MarketDataQueryGap> buildGaps(List<MarketDataQueriedCandle> candles,
                                               String timeframe,
                                               LocalDateTime windowStart,
                                               LocalDateTime windowEnd,
                                               Set<String> requestedSymbols) {
        if (candles.isEmpty() && requestedSymbols.isEmpty()) {
            return List.of();
        }

        MarketDataTimeframe requestedTimeframe = MarketDataTimeframe.from(timeframe);
        Set<String> symbols = requestedSymbols.isEmpty()
            ? candles.stream().map(MarketDataQueriedCandle::symbol).collect(Collectors.toCollection(LinkedHashSet::new))
            : requestedSymbols;
        if (symbols.isEmpty()) {
            return List.of();
        }

        Set<String> availableBuckets = candles.stream()
            .map(candle -> bucketKey(candle.symbol(), candle.timestamp()))
            .collect(Collectors.toSet());
        List<MarketDataQueryGap> gaps = new ArrayList<>();
        LocalDateTime current = alignToBucket(windowStart, requestedTimeframe);
        while (!current.isAfter(windowEnd)) {
            for (String symbol : symbols) {
                if (!availableBuckets.contains(bucketKey(symbol, current))) {
                    gaps.add(new MarketDataQueryGap(symbol, timeframe, current));
                }
            }
            current = current.plus(requestedTimeframe.step());
        }
        return gaps;
    }

    private Set<String> normalizeRequestedSymbols(Set<String> requestedSymbols) {
        if (requestedSymbols == null || requestedSymbols.isEmpty()) {
            return Set.of();
        }
        return requestedSymbols.stream()
            .filter(symbol -> symbol != null && !symbol.isBlank())
            .map(String::trim)
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<MarketDataTimeframe> finerTimeframesFor(MarketDataTimeframe requestedTimeframe) {
        return Arrays.stream(MarketDataTimeframe.values())
            .filter(candidate -> candidate.isFinerThan(requestedTimeframe))
            .sorted(Comparator.comparing(MarketDataTimeframe::step))
            .toList();
    }

    private LocalDateTime alignToBucket(LocalDateTime timestamp, MarketDataTimeframe targetTimeframe) {
        if (targetTimeframe == MarketDataTimeframe.ONE_DAY) {
            return timestamp.toLocalDate().atStartOfDay();
        }
        long stepMinutes = targetTimeframe.step().toMinutes();
        long minutesFromMidnight = timestamp.getHour() * 60L + timestamp.getMinute();
        long bucketStartMinutes = (minutesFromMidnight / stepMinutes) * stepMinutes;
        return timestamp.toLocalDate().atStartOfDay().plusMinutes(bucketStartMinutes);
    }

    private String bucketKey(String symbol, LocalDateTime bucketStart) {
        return symbol.toUpperCase(Locale.ROOT) + "|" + bucketStart;
    }

    private MarketDataQueriedCandle toQueriedCandle(MarketDataCandle candle) {
        MarketDataSeries series = candle.getSeries();
        MarketDataCandleSegment segment = candle.getSegment();
        return new MarketDataQueriedCandle(
            candle.getId().getBucketStart(),
            series.getSymbolDisplay(),
            candle.getOpenPrice(),
            candle.getHighPrice(),
            candle.getLowPrice(),
            candle.getClosePrice(),
            candle.getVolume(),
            new MarketDataCandleProvenance(
                segment.getDataset().getId(),
                segment.getImportJob() == null ? null : segment.getImportJob().getId(),
                segment.getId(),
                series.getId(),
                series.getProviderId(),
                series.getExchangeId(),
                series.getSymbolDisplay(),
                candle.getId().getTimeframe(),
                segment.getResolutionTier(),
                segment.getSourceType(),
                segment.getCoverageStart(),
                segment.getCoverageEnd()
            )
        );
    }

    private void recordQueryObservation(String scope,
                                        String scopeIdentifier,
                                        String requestedTimeframe,
                                        MarketDataQueryMode queryMode,
                                        LocalDateTime windowStart,
                                        LocalDateTime windowEnd,
                                        Set<String> normalizedSymbols,
                                        Set<String> rollupSourceTimeframes,
                                        boolean legacyFallbackUsed,
                                        long startedAt,
                                        MarketDataQueryResult result) {
        long durationNanos = System.nanoTime() - startedAt;
        long durationMillis = durationNanos / 1_000_000L;
        int distinctSegmentCount = (int) result.candles().stream()
            .map(MarketDataQueriedCandle::provenance)
            .filter(Objects::nonNull)
            .map(MarketDataCandleProvenance::segmentId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        String resultSource = classifyResultSource(result, legacyFallbackUsed);
        marketDataQueryMetrics.recordQuery(
            scope,
            queryMode,
            requestedTimeframe,
            resultSource,
            result.candles().size(),
            result.gaps().size(),
            distinctSegmentCount,
            rollupSourceTimeframes.size(),
            durationNanos
        );

        if (result.gaps().isEmpty() && distinctSegmentCount <= 1 && durationMillis < SLOW_QUERY_THRESHOLD_MILLIS) {
            return;
        }

        if (!result.gaps().isEmpty()) {
            logger.warn(
                "market_data_query event=gaps {} timeframe={} query_mode={} result_source={} candles={} gaps={} rollup_sources={} symbols={} window_start={} window_end={} duration_ms={}",
                scopeIdentifier,
                requestedTimeframe,
                queryMode,
                resultSource,
                result.candles().size(),
                result.gaps().size(),
                rollupSourceTimeframes.isEmpty() ? "<none>" : rollupSourceTimeframes,
                normalizedSymbols.isEmpty() ? "<all>" : normalizedSymbols,
                windowStart,
                windowEnd,
                durationMillis
            );
        }
        if (distinctSegmentCount > 1) {
            logger.info(
                "market_data_query event=segment_stitch {} timeframe={} query_mode={} result_source={} distinct_segments={} candles={} symbols={} window_start={} window_end={} duration_ms={}",
                scopeIdentifier,
                requestedTimeframe,
                queryMode,
                resultSource,
                distinctSegmentCount,
                result.candles().size(),
                normalizedSymbols.isEmpty() ? "<all>" : normalizedSymbols,
                windowStart,
                windowEnd,
                durationMillis
            );
        }
        if (durationMillis >= SLOW_QUERY_THRESHOLD_MILLIS) {
            logger.warn(
                "market_data_query event=slow_query {} timeframe={} query_mode={} result_source={} candles={} gaps={} symbols={} window_start={} window_end={} duration_ms={}",
                scopeIdentifier,
                requestedTimeframe,
                queryMode,
                resultSource,
                result.candles().size(),
                result.gaps().size(),
                normalizedSymbols.isEmpty() ? "<all>" : normalizedSymbols,
                windowStart,
                windowEnd,
                durationMillis
            );
        }
    }

    private String classifyResultSource(MarketDataQueryResult result, boolean legacyFallbackUsed) {
        if (legacyFallbackUsed) {
            return "legacy_csv";
        }
        if (result.candles().isEmpty()) {
            return "empty";
        }

        boolean hasRollup = result.candles().stream()
            .map(MarketDataQueriedCandle::provenance)
            .filter(Objects::nonNull)
            .map(MarketDataCandleProvenance::resolutionTier)
            .anyMatch("DERIVED_ROLLUP"::equals);
        boolean hasExact = result.candles().stream()
            .map(MarketDataQueriedCandle::provenance)
            .filter(Objects::nonNull)
            .map(MarketDataCandleProvenance::resolutionTier)
            .anyMatch("EXACT_RAW"::equals);
        if (hasRollup && hasExact) {
            return "mixed";
        }
        if (hasRollup) {
            return "rollup";
        }
        return "exact";
    }

    private record RollupAttemptResult(List<MarketDataQueriedCandle> candles,
                                       boolean sourceSeen,
                                       Set<String> sourceTimeframes) {
    }
}
