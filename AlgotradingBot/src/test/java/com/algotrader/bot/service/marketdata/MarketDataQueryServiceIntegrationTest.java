package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleId;
import com.algotrader.bot.entity.MarketDataCandleSegment;
import com.algotrader.bot.entity.MarketDataSeries;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketDataQueryServiceIntegrationTest {

    @Autowired
    private MarketDataQueryService marketDataQueryService;

    @Autowired
    private BacktestDatasetRepository backtestDatasetRepository;

    @Autowired
    private MarketDataSeriesRepository marketDataSeriesRepository;

    @Autowired
    private MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;

    @Autowired
    private MarketDataCandleRepository marketDataCandleRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void loadCandlesForDataset_prefersRelationalStoreAndKeepsProvenanceVisible() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset(
            "Relational dataset",
            "not-a-legacy-csv".getBytes()
        ));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("BTCUSDT", "BTC/USDT", "BTC", "USDT"));
        MarketDataCandleSegment segment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            series,
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00"),
            3,
            "a"
        ));

        marketDataCandleRepository.saveAndFlush(candle(series, segment, LocalDateTime.parse("2025-01-01T00:00:00"), "100"));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, LocalDateTime.parse("2025-01-01T01:00:00"), "101"));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, LocalDateTime.parse("2025-01-01T02:00:00"), "102"));

        List<MarketDataQueriedCandle> candles = marketDataQueryService.loadCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00"),
            Set.of("btcusdt")
        );

        assertThat(candles).hasSize(3);
        assertThat(candles).extracting(MarketDataQueriedCandle::symbol).containsOnly("BTC/USDT");
        assertThat(candles).extracting(candle -> candle.provenance().datasetId()).containsOnly(dataset.getId());
        assertThat(candles).extracting(candle -> candle.provenance().segmentId()).containsOnly(segment.getId());
        assertThat(candles).extracting(candle -> candle.provenance().seriesId()).containsOnly(series.getId());
        assertThat(candles).extracting(candle -> candle.provenance().sourceType()).containsOnly("UPLOAD");
        assertThat(candles).extracting(candle -> candle.provenance().resolutionTier()).containsOnly("EXACT_RAW");
    }

    @Test
    void loadCandlesForDataset_fallsBackToLegacyCsvForUnmigratedDataset() {
        byte[] csvData = """
            timestamp,symbol,open,high,low,close,volume
            2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
            2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
            2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
            """.getBytes();
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Legacy dataset", csvData));
        double legacyFallbacksBefore = counterValue(
            "algotrading.market_data.query.legacy_fallbacks",
            "scope", "dataset",
            "query_mode", "best_available",
            "requested_timeframe", "1h",
            "result_source", "legacy_csv"
        );
        double cacheMissesBefore = gaugeValue("algotrading.backtests.dataset_cache.misses");

        List<MarketDataQueriedCandle> candles = marketDataQueryService.loadCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00"),
            Set.of("BTC/USDT")
        );

        assertThat(candles).hasSize(3);
        assertThat(candles).extracting(candle -> candle.provenance().sourceType()).containsOnly("LEGACY_CSV");
        assertThat(candles).extracting(candle -> candle.provenance().resolutionTier()).containsOnly("LEGACY_FALLBACK");
        assertThat(counterValue(
            "algotrading.market_data.query.legacy_fallbacks",
            "scope", "dataset",
            "query_mode", "best_available",
            "requested_timeframe", "1h",
            "result_source", "legacy_csv"
        )).isEqualTo(legacyFallbacksBefore + 1.0d);
        assertThat(gaugeValue("algotrading.backtests.dataset_cache.misses")).isGreaterThanOrEqualTo(cacheMissesBefore + 1.0d);
    }

    @Test
    void queryCandlesForDataset_bestAvailablePrefersExactThenFillsWithRollup() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Best available dataset", "legacy".getBytes()));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("BTCUSDT", "BTC/USDT", "BTC", "USDT"));
        double rollupQueriesBefore = counterValue(
            "algotrading.market_data.query.rollup_queries",
            "scope", "dataset",
            "query_mode", "best_available",
            "requested_timeframe", "1h",
            "result_source", "mixed"
        );
        long latencySamplesBefore = timerCount(
            "algotrading.market_data.query.latency",
            "scope", "dataset",
            "query_mode", "best_available",
            "requested_timeframe", "1h",
            "result_source", "mixed"
        );

        MarketDataCandleSegment exactSegment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            series,
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T00:00:00"),
            1,
            "b"
        ));
        marketDataCandleRepository.saveAndFlush(candle(series, exactSegment, LocalDateTime.parse("2025-01-01T00:00:00"), "110"));

        MarketDataCandleSegment finerSegment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            series,
            "15m",
            LocalDateTime.parse("2025-01-01T01:00:00"),
            LocalDateTime.parse("2025-01-01T02:45:00"),
            8,
            "c"
        ));
        seedQuarterHourBlock(series, finerSegment, LocalDateTime.parse("2025-01-01T01:00:00"), "100");
        seedQuarterHourBlock(series, finerSegment, LocalDateTime.parse("2025-01-01T02:00:00"), "200");

        MarketDataQueryResult result = marketDataQueryService.queryCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:45:00"),
            Set.of("BTC/USDT"),
            MarketDataQueryMode.BEST_AVAILABLE
        );

        assertThat(result.candles()).hasSize(3);
        assertThat(result.candles()).extracting(MarketDataQueriedCandle::timestamp).containsExactly(
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T01:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00")
        );
        assertThat(result.candles().get(0).close()).isEqualByComparingTo("110");
        assertThat(result.candles().get(0).provenance().resolutionTier()).isEqualTo("EXACT_RAW");
        assertThat(result.candles().get(1).provenance().resolutionTier()).isEqualTo("DERIVED_ROLLUP");
        assertThat(result.candles().get(2).provenance().resolutionTier()).isEqualTo("DERIVED_ROLLUP");
        assertThat(result.gaps()).isEmpty();
        assertThat(counterValue(
            "algotrading.market_data.query.rollup_queries",
            "scope", "dataset",
            "query_mode", "best_available",
            "requested_timeframe", "1h",
            "result_source", "mixed"
        )).isEqualTo(rollupQueriesBefore + 1.0d);
        assertThat(timerCount(
            "algotrading.market_data.query.latency",
            "scope", "dataset",
            "query_mode", "best_available",
            "requested_timeframe", "1h",
            "result_source", "mixed"
        )).isEqualTo(latencySamplesBefore + 1L);
    }

    @Test
    void queryCandlesForDataset_exactOnlyLeavesMissingBucketsAsExplicitGaps() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Exact only dataset", "legacy".getBytes()));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("ETHUSDT", "ETH/USDT", "ETH", "USDT"));
        MarketDataCandleSegment exactSegment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            series,
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T00:00:00"),
            1,
            "d"
        ));
        marketDataCandleRepository.saveAndFlush(candle(series, exactSegment, LocalDateTime.parse("2025-01-01T00:00:00"), "120"));

        MarketDataQueryResult result = marketDataQueryService.queryCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00"),
            Set.of("ETH/USDT"),
            MarketDataQueryMode.EXACT_ONLY
        );

        assertThat(result.candles()).hasSize(1);
        assertThat(result.gaps()).extracting(MarketDataQueryGap::bucketStart).containsExactly(
            LocalDateTime.parse("2025-01-01T01:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00")
        );
    }

    @Test
    void queryCandlesForDataset_rollupRejectsIncompleteSourceBucketsAndReportsGap() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Gap dataset", "legacy".getBytes()));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("SOLUSDT", "SOL/USDT", "SOL", "USDT"));
        MarketDataCandleSegment finerSegment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            series,
            "15m",
            LocalDateTime.parse("2025-01-01T01:00:00"),
            LocalDateTime.parse("2025-01-01T01:30:00"),
            3,
            "e"
        ));

        marketDataCandleRepository.saveAndFlush(candle(series, finerSegment, LocalDateTime.parse("2025-01-01T01:00:00"), "50"));
        marketDataCandleRepository.saveAndFlush(candle(series, finerSegment, LocalDateTime.parse("2025-01-01T01:15:00"), "51"));
        marketDataCandleRepository.saveAndFlush(candle(series, finerSegment, LocalDateTime.parse("2025-01-01T01:30:00"), "52"));

        MarketDataQueryResult result = marketDataQueryService.queryCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T01:00:00"),
            LocalDateTime.parse("2025-01-01T01:00:00"),
            Set.of("SOL/USDT"),
            MarketDataQueryMode.EXACT_THEN_ROLLUP
        );

        assertThat(result.candles()).isEmpty();
        assertThat(result.gaps()).hasSize(1);
        assertThat(result.gaps().get(0).symbol()).isEqualTo("SOL/USDT");
    }

    @Test
    void queryCandlesForDataset_rollsUpMixedSymbolsIndependently() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Mixed symbols dataset", "legacy".getBytes()));
        MarketDataSeries cryptoSeries = marketDataSeriesRepository.saveAndFlush(series("BTCUSDT", "BTC/USDT", "BTC", "USDT"));
        MarketDataSeries equitySeries = marketDataSeriesRepository.saveAndFlush(series("SPY", "SPY", "", "USD", "ETF"));
        MarketDataCandleSegment cryptoSegment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            cryptoSeries,
            "15m",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T00:45:00"),
            4,
            "f"
        ));
        MarketDataCandleSegment equitySegment = marketDataCandleSegmentRepository.saveAndFlush(segment(
            dataset,
            equitySeries,
            "15m",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T00:45:00"),
            4,
            "g"
        ));

        seedQuarterHourBlock(cryptoSeries, cryptoSegment, LocalDateTime.parse("2025-01-01T00:00:00"), "300");
        seedQuarterHourBlock(equitySeries, equitySegment, LocalDateTime.parse("2025-01-01T00:00:00"), "500");

        MarketDataQueryResult result = marketDataQueryService.queryCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T00:45:00"),
            Set.of(),
            MarketDataQueryMode.BEST_AVAILABLE
        );

        assertThat(result.candles()).hasSize(2);
        assertThat(result.candles()).extracting(MarketDataQueriedCandle::symbol).containsExactly("BTC/USDT", "SPY");
        assertThat(result.candles()).allMatch(candle -> "DERIVED_ROLLUP".equals(candle.provenance().resolutionTier()));
        assertThat(result.gaps()).isEmpty();
    }

    private BacktestDataset dataset(String name, byte[] csvData) {
        BacktestDataset dataset = new BacktestDataset();
        dataset.setName(name);
        dataset.setOriginalFilename(name.replace(' ', '-').toLowerCase() + ".csv");
        dataset.setCsvData(csvData);
        dataset.setRowCount(3);
        dataset.setSymbolsCsv("BTC/USDT");
        dataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        dataset.setDataEnd(LocalDateTime.parse("2025-01-01T02:00:00"));
        dataset.setChecksumSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);
        return dataset;
    }

    private MarketDataSeries series(String symbolNormalized, String symbolDisplay, String baseAsset, String quoteAsset) {
        return series(symbolNormalized, symbolDisplay, baseAsset, quoteAsset, "CRYPTO_SPOT");
    }

    private MarketDataSeries series(String symbolNormalized,
                                    String symbolDisplay,
                                    String baseAsset,
                                    String quoteAsset,
                                    String assetClass) {
        MarketDataSeries series = new MarketDataSeries();
        series.setProviderId("stub");
        series.setBrokerId("");
        series.setExchangeId("BINANCE");
        series.setVenueType("EXCHANGE");
        series.setAssetClass(assetClass);
        series.setInstrumentType("SPOT");
        series.setSymbolNormalized(symbolNormalized);
        series.setSymbolDisplay(symbolDisplay);
        series.setBaseAsset(baseAsset);
        series.setQuoteAsset(quoteAsset);
        series.setCurrencyCode(quoteAsset);
        series.setCountryCode("");
        series.setTimezoneName("UTC");
        series.setSessionTemplate("ALWAYS_ON");
        series.setProviderMetadataJson("{\"source\":\"test\"}");
        return series;
    }

    private MarketDataCandleSegment segment(BacktestDataset dataset,
                                            MarketDataSeries series,
                                            String timeframe,
                                            LocalDateTime coverageStart,
                                            LocalDateTime coverageEnd,
                                            int rowCount,
                                            String checksumSeed) {
        MarketDataCandleSegment segment = new MarketDataCandleSegment();
        segment.setDataset(dataset);
        segment.setSeries(series);
        segment.setTimeframe(timeframe);
        segment.setSourceType("UPLOAD");
        segment.setCoverageStart(coverageStart);
        segment.setCoverageEnd(coverageEnd);
        segment.setRowCount(rowCount);
        segment.setChecksumSha256(checksumSeed.repeat(64));
        segment.setSchemaVersion("ohlcv-v1");
        segment.setResolutionTier("EXACT_RAW");
        segment.setSourcePriority((short) 100);
        segment.setSegmentStatus("ACTIVE");
        segment.setStorageEncoding("ROW_STORE");
        segment.setArchived(Boolean.FALSE);
        segment.setLineageJson("{\"kind\":\"seed\"}");
        return segment;
    }

    private MarketDataCandle candle(MarketDataSeries series,
                                    MarketDataCandleSegment segment,
                                    LocalDateTime bucketStart,
                                    String closePrice) {
        MarketDataCandle candle = new MarketDataCandle();
        candle.setId(new MarketDataCandleId(series.getId(), segment.getTimeframe(), bucketStart));
        candle.setSeries(series);
        candle.setSegment(segment);
        candle.setOpenPrice(new BigDecimal(closePrice));
        candle.setHighPrice(new BigDecimal(closePrice).add(BigDecimal.ONE));
        candle.setLowPrice(new BigDecimal(closePrice).subtract(BigDecimal.ONE));
        candle.setClosePrice(new BigDecimal(closePrice));
        candle.setVolume(new BigDecimal("1000"));
        candle.setTradeCount(42L);
        candle.setVwap(new BigDecimal(closePrice));
        return candle;
    }

    private void seedQuarterHourBlock(MarketDataSeries series,
                                      MarketDataCandleSegment segment,
                                      LocalDateTime blockStart,
                                      String baseClose) {
        BigDecimal close = new BigDecimal(baseClose);
        marketDataCandleRepository.saveAndFlush(candle(series, segment, blockStart, close.toPlainString()));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, blockStart.plusMinutes(15), close.add(BigDecimal.ONE).toPlainString()));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, blockStart.plusMinutes(30), close.add(BigDecimal.valueOf(2)).toPlainString()));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, blockStart.plusMinutes(45), close.add(BigDecimal.valueOf(3)).toPlainString()));
    }

    private double counterValue(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0d : counter.count();
    }

    private long timerCount(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        return timer == null ? 0L : timer.count();
    }

    private double gaugeValue(String name) {
        Double value = meterRegistry.get(name).gauge().value();
        return value == null ? 0.0d : value;
    }
}
