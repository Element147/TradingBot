package com.algotrader.bot.config;

import jdk.management.VirtualThreadSchedulerMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class VirtualThreadSchedulerRuntimeConfig {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadSchedulerRuntimeConfig.class);

    private final Integer configuredParallelism;

    public VirtualThreadSchedulerRuntimeConfig(
        @Value("${algotrading.runtime.virtual-thread-scheduler-parallelism:#{null}}") Integer configuredParallelism
    ) {
        this.configuredParallelism = configuredParallelism;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureVirtualThreadScheduler() {
        VirtualThreadSchedulerMXBean schedulerMXBean =
            ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);
        if (schedulerMXBean == null) {
            logger.warn("VirtualThreadSchedulerMXBean is unavailable on this runtime.");
            return;
        }

        if (configuredParallelism != null) {
            try {
                schedulerMXBean.setParallelism(configuredParallelism);
            } catch (RuntimeException exception) {
                logger.warn("Unable to set virtual-thread scheduler parallelism to {}", configuredParallelism, exception);
            }
        }

        logger.info(
            "Java runtime {} detected. Virtual-thread scheduler parallelism={}, poolSize={}, mounted={}, queued={}",
            System.getProperty("java.runtime.version"),
            schedulerMXBean.getParallelism(),
            schedulerMXBean.getPoolSize(),
            schedulerMXBean.getMountedVirtualThreadCount(),
            schedulerMXBean.getQueuedVirtualThreadCount()
        );
    }
}
