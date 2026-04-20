package com.algotrader.bot.websocket;

import com.algotrader.bot.security.JwtTokenProvider;
import com.algotrader.bot.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Authenticates WebSocket upgrade requests using the JWT access token passed in the query string.
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;

    public WebSocketAuthHandshakeInterceptor(JwtTokenProvider jwtTokenProvider, AuthService authService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   org.springframework.web.socket.WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = getQueryParam(request, "token");
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            logger.warn("Rejecting WebSocket handshake with missing or invalid token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (authService.isTokenBlacklisted(token)) {
            logger.warn("Rejecting WebSocket handshake with blacklisted token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String environment = getQueryParam(request, "env");
        if (!com.algotrader.bot.websocket.WebSocketHandler.isSupportedEnvironment(environment)) {
            logger.warn("Rejecting WebSocket handshake with invalid environment: {}", environment);
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);
        if (!StringUtils.hasText(role)) {
            logger.warn("Rejecting WebSocket handshake without an access-token role claim for user {}", username);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(com.algotrader.bot.websocket.WebSocketHandler.AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        attributes.put(com.algotrader.bot.websocket.WebSocketHandler.ENVIRONMENT_ATTRIBUTE, environment);
        attributes.put(com.algotrader.bot.websocket.WebSocketHandler.USERNAME_ATTRIBUTE, username);
        attributes.put(com.algotrader.bot.websocket.WebSocketHandler.ROLE_ATTRIBUTE, role);

        logger.debug("Accepted WebSocket handshake for user {} in {} environment", username, environment);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               org.springframework.web.socket.WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op
    }

    private String getQueryParam(ServerHttpRequest request, String name) {
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(name);
    }
}
