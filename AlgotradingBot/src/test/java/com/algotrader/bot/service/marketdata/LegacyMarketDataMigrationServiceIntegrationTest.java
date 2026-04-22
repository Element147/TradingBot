package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LegacyMarketDataMigrationServiceIntegrationTest {

    @Autowired
    private LegacyMarketDataMigrationService migrationService;

    @Autowired
    private BacktestDatasetRepository backtestDatasetRepository;

    @Autowired
    private MarketDataSeriesRepository marketDataSeriesRepository;

    @Autowired
    private MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;

    @Autowired
    private MarketDataCandleRepository marketDataCandleRepository;

    @Test
    void ingestDataset_persistsCandlesIntoNormalizedStore() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Ingest test dataset"));

        List<OHLCVData> candles = List.of(
            ohlcv("2025-01-01T00:00:00", "BTC/USDT", "100", "101", "99", "100", "10"),
            ohlcv("2025-01-01T01:00:00", "BTC/USDT", "100", "102", "99", "101", "11"),
            ohlcv("2025-01-01T02:00:00", "BTC/USDT", "101", "103", "100", "102", "12")
        );

        migrationService.ingestDataset(dataset, candles, "TEST", "Integration test ingestion.", "test-ref");

        assertThat(marketDataSeriesRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleSegmentRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleRepository.count()).isEqualTo(3);
    }

    @Test
    void ingestDataset_isIdempotentAcrossRepeatedIngestions() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Idempotent dataset"));

        List<OHLCVData> candles = List.of(
            ohlcv("2025-01-01T00:00:00", "BTC/USDT", "100", "101", "99", "100", "10"),
            ohlcv("2025-01-01T01:00:00", "BTC/USDT", "100", "102", "99", "101", "11"),
            ohlcv("2025-01-01T02:00:00", "BTC/USDT", "101", "103", "100", "102", "12")
        );

        migrationService.ingestDataset(dataset, candles, "TEST", "First ingestion.", "ref-1");
        migrationService.ingestDataset(dataset, candles, "TEST", "Second ingestion (idempotent).", "ref-1");

        assertThat(marketDataSeriesRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleSegmentRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleRepository.count()).isEqualTo(3);
    }

    @Test
    void ingestDataset_rejectsUnsupportedIntervalWithoutWritingRows() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Unsupported interval dataset"));

        // 7-minute intervals are not a supported timeframe
        List<OHLCVData> candles = List.of(
            ohlcv("2025-01-01T00:00:00", "SPY", "500", "501", "499", "500", "10"),
            ohlcv("2025-01-01T00:07:00", "SPY", "500", "502", "499", "501", "11")
        );

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
            migrationService.ingestDataset(dataset, candles, "TEST", "Should fail.", "ref")
        );

        assertThat(marketDataSeriesRepository.count()).isZero();
        assertThat(marketDataCandleSegmentRepository.count()).isZero();
        assertThat(marketDataCandleRepository.count()).isZero();
    }

    @Test
    void ingestDataset_persists_multipleSymbols() {
        BacktestDataset dataset = backtestDatasetRepository.saveAndFlush(dataset("Multi-symbol dataset"));

        List<OHLCVData> candles = List.of(
            ohlcv("2025-01-01T00:00:00", "BTC/USDT", "100", "101", "99", "100", "10"),
            ohlcv("2025-01-01T01:00:00", "BTC/USDT", "100", "102", "99", "101", "11"),
            ohlcv("2025-01-01T00:00:00", "ETH/USDT", "50", "51", "49", "50", "20"),
            ohlcv("2025-01-01T01:00:00", "ETH/USDT", "50", "52", "49", "51", "21")
        );

        migrationService.ingestDataset(dataset, candles, "TEST", "Multi-symbol ingestion.", "ref");

        // Two series (BTC and ETH), two segments, four candles
        assertThat(marketDataSeriesRepository.count()).isEqualTo(2);
        assertThat(marketDataCandleSegmentRepository.count()).isEqualTo(2);
        assertThat(marketDataCandleRepository.count()).isEqualTo(4);
    }

    private BacktestDataset dataset(String name) {
        BacktestDataset dataset = new BacktestDataset();
        dataset.setName(name);
        dataset.setOriginalFilename(name.replace(' ', '-').toLowerCase() + ".csv");
        dataset.setRowCount(3);
        dataset.setSymbolsCsv("BTC/USDT");
        dataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        dataset.setDataEnd(LocalDateTime.parse("2025-01-01T02:00:00"));
        dataset.setChecksumSha256((name + "-checksum").repeat(8).substring(0, 64));
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);
        return dataset;
    }

    private OHLCVData ohlcv(String ts, String symbol, String open, String high, String low, String close, String vol) {
        return new OHLCVData(
            LocalDateTime.parse(ts),
            symbol,
            new BigDecimal(open),
            new BigDecimal(high),
            new BigDecimal(low),
            new BigDecimal(close),
            new BigDecimal(vol)
        );
    }
}
