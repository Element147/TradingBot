package com.algotrader.bot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler for real-time updates.
 * Manages connections, subscriptions, and event publishing.
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    static final String AUTHENTICATED_ATTRIBUTE = "websocket.authenticated";
    static final String ENVIRONMENT_ATTRIBUTE = "websocket.environment";
    static final String USERNAME_ATTRIBUTE = "websocket.username";
    static final String ROLE_ATTRIBUTE = "websocket.role";
    private static final Set<String> ALLOWED_CHANNEL_SUFFIXES = Set.of(
            "balance",
            "trades",
            "positions",
            "strategies",
            "risk",
            "backtests",
            "marketData"
    );

    private final ObjectMapper objectMapper;
    
    // Store active sessions
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    
    // Store subscriptions per session (sessionId -> Set<channel>)
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    public WebSocketHandler() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    public WebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!Boolean.TRUE.equals(session.getAttributes().get(AUTHENTICATED_ATTRIBUTE))) {
            logger.warn("Rejecting unauthenticated WebSocket session {}", session.getId());
            session.close(policyViolation("Authentication required"));
            return;
        }

        String environment = getSessionEnvironment(session);
        if (!isSupportedEnvironment(environment)) {
            logger.warn("Rejecting WebSocket session {} with invalid environment {}", session.getId(), environment);
            session.close(policyViolation("Invalid environment"));
            return;
        }

        logger.info(
                "WebSocket connection established: {} user={} env={}",
                session.getId(),
                session.getAttributes().get(USERNAME_ATTRIBUTE),
                environment
        );
        sessions.add(session);
        subscriptions.put(session.getId(), new CopyOnWriteArraySet<>());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        sessions.remove(session);
        subscriptions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("Received message from {}: {}", session.getId(), payload);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            if ("subscribe".equals(type)) {
                handleSubscribe(session, data);
            } else if ("unsubscribe".equals(type)) {
                handleUnsubscribe(session, data);
            } else {
                logger.warn("Unknown message type: {}", type);
                sendError(session, "Unknown message type");
            }
        } catch (Exception e) {
            logger.error("Error processing WebSocket message", e);
            sendError(session, "Invalid message format");
        }
    }

    /**
     * Handle subscription request.
     * Expected format: {"type": "subscribe", "channels": ["test.balance", "test.trades"]}
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> data) {
        List<String> channels = parseChannels(data.get("channels"));
        if (channels.isEmpty()) {
            sendError(session, "Subscription request must include at least one valid channel");
            return;
        }

        Set<String> sessionSubs = subscriptions.get(session.getId());
        if (sessionSubs == null) {
            sendError(session, "Session is not ready for subscriptions");
            return;
        }

        String environment = getSessionEnvironment(session);
        List<String> acceptedChannels = channels.stream()
                .filter(channel -> isChannelAllowedForEnvironment(environment, channel))
                .distinct()
                .toList();
        List<String> rejectedChannels = channels.stream()
                .filter(channel -> !isChannelAllowedForEnvironment(environment, channel))
                .distinct()
                .toList();

        if (!acceptedChannels.isEmpty()) {
            sessionSubs.addAll(acceptedChannels);
            logger.info("Session {} subscribed to channels: {}", session.getId(), acceptedChannels);
            sendAck(session, "subscribed", acceptedChannels);
        }

        if (!rejectedChannels.isEmpty()) {
            logger.warn("Session {} attempted unauthorized channel subscription: {}", session.getId(), rejectedChannels);
            sendError(session, "Rejected unauthorized or unknown channels", rejectedChannels);
        }
    }

    /**
     * Handle unsubscribe request.
     * Expected format: {"type": "unsubscribe", "channels": ["test.balance"]}
     */
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> data) {
        List<String> channels = parseChannels(data.get("channels"));
        if (channels.isEmpty()) {
            sendError(session, "Unsubscribe request must include at least one valid channel");
            return;
        }

        Set<String> sessionSubs = subscriptions.get(session.getId());
        if (sessionSubs == null) {
            sendError(session, "Session is not ready for subscriptions");
            return;
        }

        String environment = getSessionEnvironment(session);
        List<String> removableChannels = channels.stream()
                .filter(channel -> isChannelAllowedForEnvironment(environment, channel))
                .distinct()
                .toList();
        List<String> rejectedChannels = channels.stream()
                .filter(channel -> !isChannelAllowedForEnvironment(environment, channel))
                .distinct()
                .toList();

        if (!removableChannels.isEmpty()) {
            sessionSubs.removeAll(removableChannels);
            logger.info("Session {} unsubscribed from channels: {}", session.getId(), removableChannels);
            sendAck(session, "unsubscribed", removableChannels);
        }

        if (!rejectedChannels.isEmpty()) {
            logger.warn("Session {} attempted unauthorized channel unsubscribe: {}", session.getId(), rejectedChannels);
            sendError(session, "Rejected unauthorized or unknown channels", rejectedChannels);
        }
    }

    /**
     * Publish event to all subscribed sessions.
     * Event format: {"type": "balance.updated", "environment": "test", "timestamp": "...", "data": {...}}
     *
     * @param channel the channel name (e.g., "test.balance", "live.trades")
     * @param event the event data
     */
    public void publishEvent(String channel, Map<String, Object> event) {
        logger.debug("Publishing event to channel {}: {}", channel, event);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                Set<String> sessionSubs = subscriptions.get(session.getId());
                if (sessionSubs != null
                        && sessionSubs.contains(channel)
                        && isChannelAllowedForEnvironment(getSessionEnvironment(session), channel)) {
                    try {
                        String json = objectMapper.writeValueAsString(event);
                        session.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        logger.error("Error sending message to session {}", session.getId(), e);
                    }
                }
            }
        }
    }

    /**
     * Send acknowledgment message to session.
     */
    private void sendAck(WebSocketSession session, String action, java.util.List<String> channels) {
        if (!session.isOpen()) {
            return;
        }

        try {
            Map<String, Object> ack = Map.of(
                    "type", "ack",
                    "action", action,
                    "channels", channels
            );
            String json = objectMapper.writeValueAsString(ack);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            logger.error("Error sending ack to session {}", session.getId(), e);
        }
    }

    /**
     * Send error message to session.
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        sendError(session, errorMessage, List.of());
    }

    private void sendError(WebSocketSession session, String errorMessage, List<String> channels) {
        if (!session.isOpen()) {
            return;
        }

        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type", "error");
            error.put("message", errorMessage);
            if (!channels.isEmpty()) {
                error.put("channels", channels);
            }
            String json = objectMapper.writeValueAsString(error);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            logger.error("Error sending error message to session {}", session.getId(), e);
        }
    }

    static boolean isSupportedEnvironment(String environment) {
        return "test".equals(environment) || "live".equals(environment);
    }

    static boolean isChannelAllowedForEnvironment(String environment, String channel) {
        if (!StringUtils.hasText(environment) || !StringUtils.hasText(channel)) {
            return false;
        }

        String expectedPrefix = environment + ".";
        if (!channel.startsWith(expectedPrefix)) {
            return false;
        }

        String suffix = channel.substring(expectedPrefix.length());
        return ALLOWED_CHANNEL_SUFFIXES.contains(suffix);
    }

    private List<String> parseChannels(Object rawChannels) {
        if (!(rawChannels instanceof List<?> channelList)) {
            return List.of();
        }

        return channelList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String getSessionEnvironment(WebSocketSession session) {
        Object environment = session.getAttributes().get(ENVIRONMENT_ATTRIBUTE);
        return environment instanceof String value ? value : null;
    }

    private CloseStatus policyViolation(String reason) {
        return new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), reason);
    }

    /**
     * Get count of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
