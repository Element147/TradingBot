package com.algotrader.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
