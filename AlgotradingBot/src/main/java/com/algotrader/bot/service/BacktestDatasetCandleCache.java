package com.algotrader.bot.service;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.BacktestDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class BacktestDatasetCandleCache {

    private static final Logger logger = LoggerFactory.getLogger(BacktestDatasetCandleCache.class);

    private final HistoricalDataCsvParser historicalDataCsvParser;
    private final ConcurrentMap<String, List<OHLCVData>> candleCache = new ConcurrentHashMap<>();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();

    public BacktestDatasetCandleCache(HistoricalDataCsvParser historicalDataCsvParser) {
        this.historicalDataCsvParser = historicalDataCsvParser;
    }

    public List<OHLCVData> getOrParse(BacktestDataset dataset) {
        String cacheKey = resolveCacheKey(dataset);
        List<OHLCVData> cached = candleCache.get(cacheKey);
        if (cached != null) {
            cacheHits.increment();
            logger.debug("Backtest candle cache hit for dataset {} ({})", dataset.getId(), dataset.getName());
            return cached;
        }

        return candleCache.computeIfAbsent(cacheKey, ignored -> {
            cacheMisses.increment();
            logger.info("Backtest candle cache miss for dataset {} ({}). Parsing CSV once for reuse.", dataset.getId(), dataset.getName());
            return List.copyOf(historicalDataCsvParser.parse(dataset.getCsvData()));
        });
    }

    private String resolveCacheKey(BacktestDataset dataset) {
        if (dataset.getChecksumSha256() != null && !dataset.getChecksumSha256().isBlank()) {
            return dataset.getChecksumSha256().trim();
        }
        return "dataset-" + Objects.requireNonNullElse(dataset.getId(), -1L);
    }

    public int size() {
        return candleCache.size();
    }

    public long cacheHits() {
        return cacheHits.sum();
    }

    public long cacheMisses() {
        return cacheMisses.sum();
    }

    public double cacheHitRatio() {
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        long total = hits + misses;
        if (total == 0L) {
            return 1.0d;
        }
        return hits / (double) total;
    }
}
