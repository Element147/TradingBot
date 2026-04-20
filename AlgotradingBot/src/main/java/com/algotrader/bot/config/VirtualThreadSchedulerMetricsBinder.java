package com.algotrader.bot.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.management.VirtualThreadSchedulerMXBean;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class VirtualThreadSchedulerMetricsBinder implements MeterBinder {

    private static final String METRIC_PREFIX = "algotrading.virtual_threads.scheduler";

    @Override
    public void bindTo(MeterRegistry registry) {
        VirtualThreadSchedulerMXBean schedulerMXBean =
            ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);
        if (schedulerMXBean == null) {
            return;
        }

        Gauge.builder(METRIC_PREFIX + ".parallelism", schedulerMXBean, VirtualThreadSchedulerMXBean::getParallelism)
            .description("Configured target parallelism for the JDK virtual-thread scheduler.")
            .register(registry);

        Gauge.builder(METRIC_PREFIX + ".pool.size", schedulerMXBean, VirtualThreadSchedulerMXBean::getPoolSize)
            .description("Current number of carrier platform threads owned by the JDK virtual-thread scheduler.")
            .register(registry);

        Gauge.builder(
                METRIC_PREFIX + ".mounted",
                schedulerMXBean,
                VirtualThreadSchedulerMXBean::getMountedVirtualThreadCount
            )
            .description("Estimated number of mounted virtual threads.")
            .register(registry);

        Gauge.builder(
                METRIC_PREFIX + ".queued",
                schedulerMXBean,
                VirtualThreadSchedulerMXBean::getQueuedVirtualThreadCount
            )
            .description("Estimated number of queued virtual threads waiting to run.")
            .register(registry);
    }
}
