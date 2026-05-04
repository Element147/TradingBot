package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleId;
import com.algotrader.bot.entity.MarketDataCandleSegment;
import com.algotrader.bot.entity.MarketDataSeries;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final MarketDataSeriesRepository marketDataSeriesRepository;
    private final MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;
    private final MarketDataCandleRepository marketDataCandleRepository;
    private final TransactionTemplate transactionTemplate;

    public LegacyMarketDataMigrationService(MarketDataSeriesRepository marketDataSeriesRepository,
                                            MarketDataCandleSegmentRepository marketDataCandleSegmentRepository,
                                            MarketDataCandleRepository marketDataCandleRepository,
                                            PlatformTransactionManager transactionManager) {
        this.marketDataSeriesRepository = marketDataSeriesRepository;
        this.marketDataCandleSegmentRepository = marketDataCandleSegmentRepository;
        this.marketDataCandleRepository = marketDataCandleRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void ingestDataset(BacktestDataset dataset,
                              List<OHLCVData> candles,
                              String sourceType,
                              String segmentNotes,
                              String providerBatchReference) {
        transactionTemplate.executeWithoutResult(status -> {
            List<SymbolMigrationPlan> plans = buildSymbolPlans(candles);
            persistDataset(dataset, plans, sourceType, segmentNotes, providerBatchReference);
        });
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

            List<com.algotrader.bot.repository.MarketDataCandleDto> existingCandles = marketDataCandleRepository.findCandlesInRange(
                series.getId(),
                plan.timeframe().id(),
                plan.coverageStart(),
                plan.coverageEnd()
            );
            Map<LocalDateTime, com.algotrader.bot.repository.MarketDataCandleDto> existingByBucket = existingCandles.stream()
                .collect(Collectors.toMap(candle -> candle.bucketStart(), candle -> candle, (left, _right) -> left, LinkedHashMap::new));

            List<OHLCVData> candlesToInsert = new ArrayList<>(plan.candles().size());
            int duplicatesForPlan = 0;
            for (OHLCVData candle : plan.candles()) {
                com.algotrader.bot.repository.MarketDataCandleDto existing = existingByBucket.get(candle.getTimestamp());
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



    private List<SymbolMigrationPlan> buildSymbolPlans(List<OHLCVData> candles) {
        List<OHLCVData> sortedCandles = candles.stream()
            .sorted(Comparator.comparing(OHLCVData::getSymbol).thenComparing(OHLCVData::getTimestamp))
            .toList();
        Map<String, List<OHLCVData>> candlesBySymbol = sortedCandles.stream()
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
                List<com.algotrader.bot.repository.MarketDataCandleDto> candles = marketDataCandleRepository.findDatasetSeriesCandlesInRange(
                    firstSegment.getDataset().getId(),
                    firstSegment.getSeries().getId(),
                    firstSegment.getTimeframe(),
                    coverageStart,
                    coverageEnd
                );
                LocalDateTime actualStart = candles.isEmpty() ? null : candles.getFirst().bucketStart();
                LocalDateTime actualEnd = candles.isEmpty() ? null : candles.getLast().bucketStart();
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

    private boolean matches(com.algotrader.bot.repository.MarketDataCandleDto existing, OHLCVData candle) {
        return existing.openPrice().compareTo(candle.getOpen()) == 0
            && existing.highPrice().compareTo(candle.getHigh()) == 0
            && existing.lowPrice().compareTo(candle.getLow()) == 0
            && existing.closePrice().compareTo(candle.getClose()) == 0
            && existing.volume().compareTo(candle.getVolume()) == 0;
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

    private String checksumForMarketDataCandles(List<com.algotrader.bot.repository.MarketDataCandleDto> candles) {
        return checksumForStrings(candles.stream()
            .map(candle -> candle.bucketStart() + "|" + candle.symbolDisplay() + "|"
                + candle.openPrice() + "|" + candle.highPrice() + "|" + candle.lowPrice() + "|"
                + candle.closePrice() + "|" + candle.volume())
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
