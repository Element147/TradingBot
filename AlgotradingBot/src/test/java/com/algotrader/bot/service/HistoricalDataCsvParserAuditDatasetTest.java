package com.algotrader.bot.service;

import com.algotrader.bot.backtest.OHLCVData;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalDataCsvParserAuditDatasetTest {

    private final HistoricalDataCsvParser parser = new HistoricalDataCsvParser();

    @Test
    void parsesCheckedInEtfAuditDataset() throws Exception {
        Path datasetPath = Path.of(
            "..",
            "docs",
            "audit-datasets",
            "us-etf-daily-small-account-pack-2024-03-12-to-2026-03-12.csv"
        );

        List<OHLCVData> candles = parser.parse(Files.readAllBytes(datasetPath));

        assertThat(candles).hasSize(2008);
        assertThat(candles)
            .extracting(OHLCVData::getSymbol)
            .contains("SPY", "QQQ", "VTI", "VT");
        assertThat(candles.getFirst().getTimestamp()).isEqualTo(LocalDateTime.parse("2024-03-12T00:00:00"));
        assertThat(candles.getLast().getTimestamp()).isEqualTo(LocalDateTime.parse("2026-03-12T00:00:00"));
    }
}
