package com.algotrader.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application entry point for the Algorithmic Trading Bot.
 * 
 * This application provides:
 * - Risk management layer with 2% position sizing
 * - Bollinger Bands mean reversion trading strategy
 * - Backtesting engine with validation
 * - REST API for strategy control
 * - PostgreSQL persistence
 * - Prometheus metrics
 * - Structured JSON logging
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.algotrader.bot.repository")
@EnableAsync
@EnableScheduling
public class BotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }
}
