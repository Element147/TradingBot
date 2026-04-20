package com.algotrader.bot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Algorithmic Trading Bot API")
                .version("1.0.0")
                .description("Production-grade cryptocurrency algorithmic trading bot API. " +
                           "Provides endpoints for managing trading strategies, monitoring performance, " +
                           "viewing trade history, and accessing backtest results. " +
                           "\n\n**Core Strategy:** Bollinger Bands Mean Reversion on BTC/USDT and ETH/USDT pairs. " +
                           "\n\n**Risk Management:** Maximum 2% risk per trade, 25% max drawdown limit, circuit breakers enabled.")
                .contact(new Contact()
                    .name("AlgoTrader Support")
                    .email("support@algotrader.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local development server"),
                new Server()
                    .url("https://api.algotrader.com")
                    .description("Production server")
            ));
    }
}
