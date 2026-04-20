package com.algotrader.bot.config;

import com.algotrader.bot.websocket.WebSocketHandler;
import com.algotrader.bot.websocket.WebSocketAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * WebSocket configuration for real-time updates.
 * Configures WebSocket endpoint and handler.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(WebSocketHandler webSocketHandler,
                           WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor,
                           @Value("${algotrading.websocket.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173}") String allowedOriginPatterns) {
        this.webSocketHandler = webSocketHandler;
        this.webSocketAuthHandshakeInterceptor = webSocketAuthHandshakeInterceptor;
        String[] parsedOriginPatterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        this.allowedOriginPatterns = parsedOriginPatterns.length == 0
                ? new String[]{
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:4173",
                    "http://127.0.0.1:4173"
                }
                : parsedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .addInterceptors(webSocketAuthHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
