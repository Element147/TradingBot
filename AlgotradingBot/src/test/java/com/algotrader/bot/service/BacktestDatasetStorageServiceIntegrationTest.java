package com.algotrader.bot.service;

import com.algotrader.bot.controller.BacktestDatasetDownloadResponse;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import com.algotrader.bot.service.marketdata.MarketDataQueriedCandle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BacktestDatasetStorageServiceIntegrationTest {

    @Autowired
    private BacktestDatasetStorageService backtestDatasetStorageService;

    @Autowired
    private MarketDataQueryService marketDataQueryService;

    @Autowired
    private MarketDataSeriesRepository marketDataSeriesRepository;

    @Autowired
    private MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;

    @Autowired
    private MarketDataCandleRepository marketDataCandleRepository;

    @Test
    void storeUploadedDataset_persistsNormalizedCandlesForImmediateQueries() {
        byte[] csvData = csv(
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,BTC/USDT,100,101,99,100,10
                2025-01-01T01:00:00,BTC/USDT,100,102,99,101,11
                2025-01-01T02:00:00,BTC/USDT,101,103,100,102,12
                """
        );

        BacktestDataset dataset = backtestDatasetStorageService.storeUploadedDataset(
            "Uploaded dataset",
            new MockMultipartFile("file", "uploaded.csv", "text/csv", csvData)
        );

        assertThat(marketDataSeriesRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleSegmentRepository.count()).isEqualTo(1);
        assertThat(marketDataCandleRepository.count()).isEqualTo(3);

        List<MarketDataQueriedCandle> candles = marketDataQueryService.loadCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00"),
            Set.of("BTC/USDT")
        );

        assertThat(candles).hasSize(3);
        assertThat(candles).extracting(candle -> candle.provenance().sourceType()).containsOnly("UPLOAD");
        assertThat(candles).extracting(candle -> candle.provenance().resolutionTier()).containsOnly("EXACT_RAW");
    }

    @Test
    void storeImportedDataset_generatesDownloadsFromNormalizedStoreWhenCompatibilityBlobBecomesStale() {
        byte[] csvData = csv(
            """
                timestamp,symbol,open,high,low,close,volume
                2025-01-01T00:00:00,ETH/USDT,200,201,199,200,20
                2025-01-01T01:00:00,ETH/USDT,200,202,199,201,21
                2025-01-01T02:00:00,ETH/USDT,201,203,200,202,22
                """
        );

        BacktestDataset dataset = backtestDatasetStorageService.storeImportedDataset(
            "Imported dataset",
            "provider-import.csv",
            csvData,
            "Test Provider"
        );
        dataset.setCsvData("corrupted,compatibility,blob".getBytes());

        BacktestDatasetDownloadResponse downloadResponse = backtestDatasetStorageService.downloadDataset(dataset.getId());
        List<MarketDataQueriedCandle> candles = marketDataQueryService.loadCandlesForDataset(
            dataset.getId(),
            "1h",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T02:00:00"),
            Set.of("ETH/USDT")
        );

        assertThat(new String(downloadResponse.csvData())).isEqualTo(new String(csvData));
        assertThat(downloadResponse.exportSource()).isEqualTo("NORMALIZED_EXPORT");
        assertThat(candles).hasSize(3);
        assertThat(candles).extracting(candle -> candle.provenance().sourceType()).containsOnly("IMPORT_JOB");
        assertThat(candles).extracting(candle -> candle.provenance().datasetId()).containsOnly(dataset.getId());
    }

    private byte[] csv(String body) {
        return body.stripIndent().trim().getBytes();
    }
}
