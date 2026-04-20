package com.algotrader.bot.repository;

import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleId;
import com.algotrader.bot.entity.MarketDataCandleSegment;
import com.algotrader.bot.entity.MarketDataSeries;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketDataRepositoryIntegrationTest {

    @Autowired
    private BacktestDatasetRepository backtestDatasetRepository;

    @Autowired
    private MarketDataSeriesRepository marketDataSeriesRepository;

    @Autowired
    private MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;

    @Autowired
    private MarketDataCandleRepository marketDataCandleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void seriesRepositorySupportsVenueAndAssetFilters() {
        marketDataSeriesRepository.saveAndFlush(series("stub", "BINANCE", "BTCUSDT", "BTC/USDT", "CRYPTO_SPOT", "BTC", "USDT"));
        marketDataSeriesRepository.saveAndFlush(series("stub", "BINANCE", "ETHUSDT", "ETH/USDT", "CRYPTO_SPOT", "ETH", "USDT"));
        marketDataSeriesRepository.saveAndFlush(series("stub", "NASDAQ", "SPY", "SPY", "ETF", "", "USD"));

        MarketDataSeries btcSeries = marketDataSeriesRepository
            .findByProviderIdAndExchangeIdAndSymbolNormalizedAndAssetClass("stub", "BINANCE", "BTCUSDT", "CRYPTO_SPOT")
            .orElseThrow();

        List<MarketDataSeries> quotePairSeries = marketDataSeriesRepository
            .findByAssetClassAndBaseAssetAndQuoteAssetOrderBySymbolNormalizedAsc("CRYPTO_SPOT", "BTC", "USDT");

        assertThat(btcSeries.getSymbolDisplay()).isEqualTo("BTC/USDT");
        assertThat(quotePairSeries).extracting(MarketDataSeries::getSymbolNormalized).containsExactly("BTCUSDT");
    }

    @Test
    void candleRepositoryReadsRangeInBucketOrder() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Range read dataset"));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("stub", "BINANCE", "BTCUSDT", "BTC/USDT", "CRYPTO_SPOT", "BTC", "USDT"));
        MarketDataCandleSegment segment = marketDataCandleSegmentRepository.saveAndFlush(
            segment(dataset, series, "1h", LocalDateTime.parse("2025-01-01T00:00:00"), LocalDateTime.parse("2025-01-01T05:00:00"), 3, "a")
        );

        marketDataCandleRepository.saveAndFlush(candle(series, segment, LocalDateTime.parse("2025-01-01T02:00:00"), "102"));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, LocalDateTime.parse("2025-01-01T00:00:00"), "100"));
        marketDataCandleRepository.saveAndFlush(candle(series, segment, LocalDateTime.parse("2025-01-01T01:00:00"), "101"));

        List<MarketDataCandle> candles = marketDataCandleRepository.findCandlesInRange(
            series.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00")
        );

        assertThat(candles).extracting(candle -> candle.getId().getBucketStart()).containsExactly(
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T01:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00")
        );
        assertThat(candles).extracting(MarketDataCandle::getClosePrice).containsExactly(
            new BigDecimal("100"),
            new BigDecimal("101"),
            new BigDecimal("102")
        );
    }

    @Test
    void segmentRepositoryFindsCoverageOverlaps() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Overlap dataset"));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("stub", "BINANCE", "ETHUSDT", "ETH/USDT", "CRYPTO_SPOT", "ETH", "USDT"));

        marketDataCandleSegmentRepository.saveAndFlush(
            segment(dataset, series, "15m", LocalDateTime.parse("2025-01-01T00:00:00"), LocalDateTime.parse("2025-01-01T04:00:00"), 16, "b")
        );
        marketDataCandleSegmentRepository.saveAndFlush(
            segment(dataset, series, "15m", LocalDateTime.parse("2025-01-01T03:00:00"), LocalDateTime.parse("2025-01-01T08:00:00"), 20, "c")
        );
        marketDataCandleSegmentRepository.saveAndFlush(
            segment(dataset, series, "1h", LocalDateTime.parse("2025-01-01T00:00:00"), LocalDateTime.parse("2025-01-01T08:00:00"), 8, "d")
        );

        List<MarketDataCandleSegment> overlaps = marketDataCandleSegmentRepository.findOverlappingSegments(
            series.getId(),
            "15m",
            LocalDateTime.parse("2025-01-01T02:30:00"),
            LocalDateTime.parse("2025-01-01T03:30:00")
        );

        assertThat(overlaps).hasSize(2);
        assertThat(overlaps).extracting(MarketDataCandleSegment::getCoverageStart).containsExactly(
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T03:00:00")
        );
    }

    @Test
    void candlePrimaryKeyPreventsDuplicateBucketsForSameSeriesAndTimeframe() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Duplicate guard dataset"));
        MarketDataSeries series = marketDataSeriesRepository.saveAndFlush(series("stub", "BINANCE", "SOLUSDT", "SOL/USDT", "CRYPTO_SPOT", "SOL", "USDT"));
        MarketDataCandleSegment segment = marketDataCandleSegmentRepository.saveAndFlush(
            segment(dataset, series, "1h", LocalDateTime.parse("2025-01-01T00:00:00"), LocalDateTime.parse("2025-01-01T01:00:00"), 1, "e")
        );

        MarketDataCandle first = candle(series, segment, LocalDateTime.parse("2025-01-01T00:00:00"), "100");
        marketDataCandleRepository.saveAndFlush(first);
        entityManager.clear();

        MarketDataCandle duplicate = candle(
            reference(MarketDataSeries.class, series.getId()),
            reference(MarketDataCandleSegment.class, segment.getId()),
            LocalDateTime.parse("2025-01-01T00:00:00"),
            "101"
        );
        duplicate.setCreatedAt(LocalDateTime.now());

        assertThrows(PersistenceException.class, () -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        });
    }

    private BacktestDataset dataset(String name) {
        BacktestDataset dataset = new BacktestDataset();
        dataset.setName(name);
        dataset.setOriginalFilename(name.replace(' ', '-').toLowerCase() + ".csv");
        dataset.setCsvData("timestamp,symbol,open,high,low,close,volume".getBytes());
        dataset.setRowCount(3);
        dataset.setSymbolsCsv("BTC/USDT");
        dataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        dataset.setDataEnd(LocalDateTime.parse("2025-01-01T02:00:00"));
        dataset.setChecksumSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);
        return dataset;
    }

    private MarketDataSeries series(
        String providerId,
        String exchangeId,
        String symbolNormalized,
        String symbolDisplay,
        String assetClass,
        String baseAsset,
        String quoteAsset
    ) {
        MarketDataSeries series = new MarketDataSeries();
        series.setProviderId(providerId);
        series.setBrokerId("");
        series.setExchangeId(exchangeId);
        series.setVenueType("EXCHANGE");
        series.setAssetClass(assetClass);
        series.setInstrumentType("SPOT");
        series.setSymbolNormalized(symbolNormalized);
        series.setSymbolDisplay(symbolDisplay);
        series.setBaseAsset(baseAsset);
        series.setQuoteAsset(quoteAsset);
        series.setCurrencyCode(quoteAsset);
        series.setCountryCode(assetClass.equals("ETF") ? "US" : "");
        series.setTimezoneName("UTC");
        series.setSessionTemplate(assetClass.equals("ETF") ? "US_EQUITIES" : "ALWAYS_ON");
        series.setProviderMetadataJson("{\"source\":\"test\"}");
        return series;
    }

    private MarketDataCandleSegment segment(
        BacktestDataset dataset,
        MarketDataSeries series,
        String timeframe,
        LocalDateTime coverageStart,
        LocalDateTime coverageEnd,
        int rowCount,
        String checksumSeed
    ) {
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

    private MarketDataCandle candle(
        MarketDataSeries series,
        MarketDataCandleSegment segment,
        LocalDateTime bucketStart,
        String closePrice
    ) {
        MarketDataCandle candle = new MarketDataCandle();
        candle.setId(new MarketDataCandleId(series.getId(), segment.getTimeframe(), bucketStart));
        candle.setSeries(series);
        candle.setSegment(segment);
        candle.setOpenPrice(new BigDecimal(closePrice));
        candle.setHighPrice(new BigDecimal(closePrice).add(new BigDecimal("1")));
        candle.setLowPrice(new BigDecimal(closePrice).subtract(new BigDecimal("1")));
        candle.setClosePrice(new BigDecimal(closePrice));
        candle.setVolume(new BigDecimal("1000"));
        candle.setTradeCount(42L);
        candle.setVwap(new BigDecimal(closePrice));
        return candle;
    }

    private <T> T reference(Class<T> entityType, Long id) {
        return entityManager.getReference(entityType, id);
    }
}
