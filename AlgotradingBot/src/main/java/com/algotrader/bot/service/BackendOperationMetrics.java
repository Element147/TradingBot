package com.algotrader.bot.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class BackendOperationMetrics {

    private static final Logger logger = LoggerFactory.getLogger(BackendOperationMetrics.class);
    private static final long SLOW_OPERATION_THRESHOLD_MS = 250L;

    private final MeterRegistry meterRegistry;

    public BackendOperationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String category,
                       String operation,
                       String stage,
                       long durationNanos,
                       int itemCount,
                       long payloadBytes) {
        String normalizedCategory = normalizeTag(category);
        String normalizedOperation = normalizeTag(operation);
        String normalizedStage = normalizeTag(stage);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

        Timer.builder("algotrading.backend.operation.latency")
            .description("Latency for backend read, publish, and async workflow operations.")
            .tag("category", normalizedCategory)
            .tag("operation", normalizedOperation)
            .tag("stage", normalizedStage)
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        DistributionSummary.builder("algotrading.backend.operation.items")
            .description("Item counts emitted or processed by backend operations.")
            .baseUnit("items")
            .tag("category", normalizedCategory)
            .tag("operation", normalizedOperation)
            .tag("stage", normalizedStage)
            .register(meterRegistry)
            .record(Math.max(0, itemCount));

        DistributionSummary.builder("algotrading.backend.operation.payload_bytes")
            .description("Approximate payload size for backend operations.")
            .baseUnit("bytes")
            .tag("category", normalizedCategory)
            .tag("operation", normalizedOperation)
            .tag("stage", normalizedStage)
            .register(meterRegistry)
            .record(Math.max(0L, payloadBytes));

        Counter.builder("algotrading.backend.operation.executions")
            .description("Number of profiled backend operations.")
            .tag("category", normalizedCategory)
            .tag("operation", normalizedOperation)
            .tag("stage", normalizedStage)
            .register(meterRegistry)
            .increment();

        if (durationMs >= SLOW_OPERATION_THRESHOLD_MS) {
            Counter.builder("algotrading.backend.operation.slow_executions")
                .description("Number of profiled backend operations slower than the threshold.")
                .tag("category", normalizedCategory)
                .tag("operation", normalizedOperation)
                .tag("stage", normalizedStage)
                .register(meterRegistry)
                .increment();
        }

        logger.info(
            "backend_profile category={} operation={} stage={} duration_ms={} item_count={} payload_bytes={}",
            normalizedCategory,
            normalizedOperation,
            normalizedStage,
            durationMs,
            Math.max(0, itemCount),
            Math.max(0L, payloadBytes)
        );
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
