package com.algotrader.bot.service.marketdata;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MarketDataQueryMetrics {

    private final MeterRegistry meterRegistry;

    public MarketDataQueryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordQuery(String scope,
                            MarketDataQueryMode queryMode,
                            String requestedTimeframe,
                            String resultSource,
                            int candleCount,
                            int gapCount,
                            int distinctSegmentCount,
                            int rollupSourceCount,
                            long durationNanos) {
        String normalizedResultSource = normalizeTag(resultSource);
        String normalizedTimeframe = normalizeTag(requestedTimeframe);
        String normalizedMode = queryMode == null ? "unknown" : normalizeTag(queryMode.name());

        Timer.builder("algotrading.market_data.query.latency")
            .description("Latency for normalized market-data reads.")
            .tag("scope", normalizeTag(scope))
            .tag("query_mode", normalizedMode)
            .tag("requested_timeframe", normalizedTimeframe)
            .tag("result_source", normalizedResultSource)
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        Counter.builder("algotrading.market_data.query.executions")
            .description("Number of normalized market-data queries.")
            .tag("scope", normalizeTag(scope))
            .tag("query_mode", normalizedMode)
            .tag("requested_timeframe", normalizedTimeframe)
            .tag("result_source", normalizedResultSource)
            .register(meterRegistry)
            .increment();

        DistributionSummary.builder("algotrading.market_data.query.candles")
            .description("Candles returned by normalized market-data queries.")
            .baseUnit("candles")
            .tag("scope", normalizeTag(scope))
            .tag("query_mode", normalizedMode)
            .tag("requested_timeframe", normalizedTimeframe)
            .tag("result_source", normalizedResultSource)
            .register(meterRegistry)
            .record(candleCount);

        DistributionSummary.builder("algotrading.market_data.query.gaps")
            .description("Gap buckets reported by normalized market-data queries.")
            .baseUnit("buckets")
            .tag("scope", normalizeTag(scope))
            .tag("query_mode", normalizedMode)
            .tag("requested_timeframe", normalizedTimeframe)
            .tag("result_source", normalizedResultSource)
            .register(meterRegistry)
            .record(gapCount);

        DistributionSummary.builder("algotrading.market_data.query.segments")
            .description("Distinct provenance segments stitched into normalized market-data query results.")
            .baseUnit("segments")
            .tag("scope", normalizeTag(scope))
            .tag("query_mode", normalizedMode)
            .tag("requested_timeframe", normalizedTimeframe)
            .tag("result_source", normalizedResultSource)
            .register(meterRegistry)
            .record(distinctSegmentCount);

        if (gapCount > 0) {
            taggedCounter("algotrading.market_data.query.queries_with_gaps", scope, normalizedMode, normalizedTimeframe, normalizedResultSource)
                .increment();
        }
        if (rollupSourceCount > 0) {
            taggedCounter("algotrading.market_data.query.rollup_queries", scope, normalizedMode, normalizedTimeframe, normalizedResultSource)
                .increment();
        }
        if ("legacy_csv".equals(normalizedResultSource)) {
            taggedCounter("algotrading.market_data.query.legacy_fallbacks", scope, normalizedMode, normalizedTimeframe, normalizedResultSource)
                .increment();
        }
        if (distinctSegmentCount > 1) {
            taggedCounter("algotrading.market_data.query.segment_stitch_queries", scope, normalizedMode, normalizedTimeframe, normalizedResultSource)
                .increment();
        }
        if (TimeUnit.NANOSECONDS.toMillis(durationNanos) >= 500L) {
            taggedCounter("algotrading.market_data.query.slow_queries", scope, normalizedMode, normalizedTimeframe, normalizedResultSource)
                .increment();
        }
    }

    private Counter taggedCounter(String name,
                                  String scope,
                                  String queryMode,
                                  String requestedTimeframe,
                                  String resultSource) {
        return Counter.builder(name)
            .tag("scope", normalizeTag(scope))
            .tag("query_mode", queryMode)
            .tag("requested_timeframe", requestedTimeframe)
            .tag("result_source", resultSource)
            .register(meterRegistry);
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase().replace(' ', '_');
    }
}
