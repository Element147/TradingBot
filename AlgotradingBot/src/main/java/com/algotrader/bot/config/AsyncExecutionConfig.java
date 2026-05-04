package com.algotrader.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class AsyncExecutionConfig {

    @Bean(name = {"taskExecutor", "virtualThreadTaskExecutor"}, destroyMethod = "close")
    public ExecutorService virtualThreadTaskExecutor() {
        ThreadFactory threadFactory = Thread.ofVirtual()
            .name("algotrading-vt-", 0)
            .factory();
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }

    @Bean(name = "backtestTaskExecutor")
    public ThreadPoolTaskExecutor backtestTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(400);
        executor.setThreadNamePrefix("backtest-exec-");
        executor.initialize();
        return executor;
    }
}

