package com.algotrader.bot.config;

import com.algotrader.bot.service.BacktestDatasetCandleCache;
import com.algotrader.bot.service.BacktestExecutionService;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.repository.MarketDataSeriesRepository;
import com.algotrader.bot.service.marketdata.MarketDataImportService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class OperationalWorkloadMetricsBinder implements MeterBinder {

    private final BacktestExecutionService backtestExecutionService;
    private final MarketDataImportService marketDataImportService;
    private final BacktestDatasetCandleCache backtestDatasetCandleCache;
    private final MarketDataSeriesRepository marketDataSeriesRepository;
    private final MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;
    private final MarketDataCandleRepository marketDataCandleRepository;

    public OperationalWorkloadMetricsBinder(BacktestExecutionService backtestExecutionService,
                                            MarketDataImportService marketDataImportService,
                                            BacktestDatasetCandleCache backtestDatasetCandleCache,
                                            MarketDataSeriesRepository marketDataSeriesRepository,
                                            MarketDataCandleSegmentRepository marketDataCandleSegmentRepository,
                                            MarketDataCandleRepository marketDataCandleRepository) {
        this.backtestExecutionService = backtestExecutionService;
        this.marketDataImportService = marketDataImportService;
        this.backtestDatasetCandleCache = backtestDatasetCandleCache;
        this.marketDataSeriesRepository = marketDataSeriesRepository;
        this.marketDataCandleSegmentRepository = marketDataCandleSegmentRepository;
        this.marketDataCandleRepository = marketDataCandleRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("algotrading.backtests.inflight", backtestExecutionService, BacktestExecutionService::getInFlightBacktestCount)
            .description("Number of backtests currently running or scheduled in this JVM.")
            .register(registry);

        Gauge.builder("algotrading.market_data.imports.dispatched", marketDataImportService, MarketDataImportService::getLocallyDispatchedJobCount)
            .description("Number of market-data import jobs currently dispatched in this JVM.")
            .register(registry);

        Gauge.builder("algotrading.backtests.dataset_cache.entries", backtestDatasetCandleCache, BacktestDatasetCandleCache::size)
            .description("Number of parsed datasets currently retained in the backtest candle cache.")
            .register(registry);

        Gauge.builder("algotrading.backtests.dataset_cache.hits", backtestDatasetCandleCache, BacktestDatasetCandleCache::cacheHits)
            .description("Number of legacy dataset cache hits observed in this JVM.")
            .register(registry);

        Gauge.builder("algotrading.backtests.dataset_cache.misses", backtestDatasetCandleCache, BacktestDatasetCandleCache::cacheMisses)
            .description("Number of legacy dataset cache misses observed in this JVM.")
            .register(registry);

        Gauge.builder("algotrading.backtests.dataset_cache.hit_ratio", backtestDatasetCandleCache, BacktestDatasetCandleCache::cacheHitRatio)
            .description("Hit ratio for the legacy dataset candle cache.")
            .register(registry);

        Gauge.builder("algotrading.market_data.store.series", marketDataSeriesRepository, MarketDataSeriesRepository::count)
            .description("Number of normalized market-data series in the relational store.")
            .register(registry);

        Gauge.builder("algotrading.market_data.store.segments", marketDataCandleSegmentRepository, MarketDataCandleSegmentRepository::count)
            .description("Number of normalized market-data candle segments in the relational store.")
            .register(registry);

        Gauge.builder("algotrading.market_data.store.candles", marketDataCandleRepository, MarketDataCandleRepository::count)
            .description("Number of normalized market-data candles in the relational store.")
            .register(registry);
    }
}
