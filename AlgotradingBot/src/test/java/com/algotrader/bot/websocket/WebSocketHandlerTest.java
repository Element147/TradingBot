package com.algotrader.bot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketHandler.
 * Tests connection management, subscriptions, and event publishing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketHandlerTest {

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private WebSocketHandler webSocketHandler;
    private Map<String, Object> session1Attributes;
    private Map<String, Object> session2Attributes;

    @BeforeEach
    void setUp() {
        webSocketHandler = new WebSocketHandler(new ObjectMapper().findAndRegisterModules());
        session1Attributes = new HashMap<>();
        session1Attributes.put(WebSocketHandler.AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        session1Attributes.put(WebSocketHandler.ENVIRONMENT_ATTRIBUTE, "test");
        session1Attributes.put(WebSocketHandler.USERNAME_ATTRIBUTE, "session-user-1");
        session1Attributes.put(WebSocketHandler.ROLE_ATTRIBUTE, "TRADER");
        session2Attributes = new HashMap<>();
        session2Attributes.put(WebSocketHandler.AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        session2Attributes.put(WebSocketHandler.ENVIRONMENT_ATTRIBUTE, "test");
        session2Attributes.put(WebSocketHandler.USERNAME_ATTRIBUTE, "session-user-2");
        session2Attributes.put(WebSocketHandler.ROLE_ATTRIBUTE, "TRADER");
        when(session1.getId()).thenReturn("session-1");
        when(session2.getId()).thenReturn("session-2");
        when(session1.getAttributes()).thenReturn(session1Attributes);
        when(session2.getAttributes()).thenReturn(session2Attributes);
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
    }

    @Test
    void testConnectionEstablished() throws Exception {
        // Act
        webSocketHandler.afterConnectionEstablished(session1);

        // Assert
        assertEquals(1, webSocketHandler.getActiveSessionCount());
    }

    @Test
    void testConnectionClosed() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        assertEquals(1, webSocketHandler.getActiveSessionCount());

        // Act
        webSocketHandler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        // Assert
        assertEquals(0, webSocketHandler.getActiveSessionCount());
    }

    @Test
    void testMultipleConnections() throws Exception {
        // Act
        webSocketHandler.afterConnectionEstablished(session1);
        webSocketHandler.afterConnectionEstablished(session2);

        // Assert
        assertEquals(2, webSocketHandler.getActiveSessionCount());
    }

    @Test
    void testSubscribeToChannels() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"test.balance\",\"test.trades\"]}";

        // Act
        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));

        // Assert
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, atLeastOnce()).sendMessage(messageCaptor.capture());
        
        // Verify acknowledgment was sent
        String ackMessage = messageCaptor.getValue().getPayload();
        assertTrue(ackMessage.contains("ack") || ackMessage.contains("subscribed"));
    }

    @Test
    void testUnsubscribeFromChannels() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"test.balance\"]}";
        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));
        
        String unsubscribeMessage = "{\"type\":\"unsubscribe\",\"channels\":[\"test.balance\"]}";

        // Act
        webSocketHandler.handleTextMessage(session1, new TextMessage(unsubscribeMessage));

        // Assert
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, atLeastOnce()).sendMessage(messageCaptor.capture());
        
        // Verify acknowledgment was sent
        List<TextMessage> messages = messageCaptor.getAllValues();
        assertTrue(messages.size() >= 2); // Subscribe ack + Unsubscribe ack
    }

    @Test
    void testPublishEventToSubscribedSession() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"test.balance\"]}";
        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));

        Map<String, Object> event = new HashMap<>();
        event.put("type", "balance.updated");
        event.put("environment", "test");
        event.put("data", Map.of("total", "1000.00"));

        // Act
        webSocketHandler.publishEvent("test.balance", event);

        // Assert
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, atLeastOnce()).sendMessage(messageCaptor.capture());
        
        // Verify event was sent (last message should be the event)
        List<TextMessage> messages = messageCaptor.getAllValues();
        String lastMessage = messages.get(messages.size() - 1).getPayload();
        assertTrue(lastMessage.contains("balance.updated"));
    }

    @Test
    void testPublishEventToUnsubscribedSession() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        // No subscription

        Map<String, Object> event = new HashMap<>();
        event.put("type", "balance.updated");
        event.put("environment", "test");
        event.put("data", Map.of("total", "1000.00"));

        // Act
        webSocketHandler.publishEvent("test.balance", event);

        // Assert
        // Session should not receive the event (no subscription)
        verify(session1, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void testPublishEventToMultipleSessions() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        webSocketHandler.afterConnectionEstablished(session2);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"test.balance\"]}";
        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));
        webSocketHandler.handleTextMessage(session2, new TextMessage(subscribeMessage));

        Map<String, Object> event = new HashMap<>();
        event.put("type", "balance.updated");
        event.put("environment", "test");
        event.put("data", Map.of("total", "1000.00"));

        // Act
        webSocketHandler.publishEvent("test.balance", event);

        // Assert
        verify(session1, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(session2, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    void testInvalidMessageFormat() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        String invalidMessage = "invalid json";

        // Act
        webSocketHandler.handleTextMessage(session1, new TextMessage(invalidMessage));

        // Assert
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, atLeastOnce()).sendMessage(messageCaptor.capture());
        
        // Verify error message was sent
        String errorMessage = messageCaptor.getValue().getPayload();
        assertTrue(errorMessage.contains("error") || errorMessage.contains("Invalid"));
    }

    @Test
    void testUnknownMessageType() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        String unknownMessage = "{\"type\":\"unknown\",\"data\":{}}";

        // Act
        webSocketHandler.handleTextMessage(session1, new TextMessage(unknownMessage));

        // Assert
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getPayload().contains("Unknown message type"));
    }

    @Test
    void testPublishEventToClosedSession() throws Exception {
        // Arrange
        webSocketHandler.afterConnectionEstablished(session1);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"test.balance\"]}";
        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));
        
        when(session1.isOpen()).thenReturn(false); // Simulate closed session

        Map<String, Object> event = new HashMap<>();
        event.put("type", "balance.updated");
        event.put("environment", "test");
        event.put("data", Map.of("total", "1000.00"));

        // Act
        webSocketHandler.publishEvent("test.balance", event);

        // Assert
        // Should not attempt to send to closed session
        verify(session1, atMost(1)).sendMessage(any(TextMessage.class)); // Only the subscribe ack
    }

    @Test
    void testRejectsUnauthenticatedSession() throws Exception {
        session1Attributes.put(WebSocketHandler.AUTHENTICATED_ATTRIBUTE, Boolean.FALSE);

        webSocketHandler.afterConnectionEstablished(session1);

        verify(session1).close(argThat(status ->
                status != null
                        && status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                        && status.getReason() != null
                        && status.getReason().contains("Authentication required")));
        assertEquals(0, webSocketHandler.getActiveSessionCount());
    }

    @Test
    void testRejectsCrossEnvironmentSubscription() throws Exception {
        webSocketHandler.afterConnectionEstablished(session1);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"live.balance\"]}";

        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(messageCaptor.capture());
        String payload = messageCaptor.getValue().getPayload();
        assertTrue(payload.contains("\"type\":\"error\""));
        assertTrue(payload.contains("Rejected unauthorized or unknown channels"));
        assertTrue(payload.contains("live.balance"));
    }

    @Test
    void testPartiallyAcceptsMixedEnvironmentSubscription() throws Exception {
        webSocketHandler.afterConnectionEstablished(session1);
        String subscribeMessage = "{\"type\":\"subscribe\",\"channels\":[\"test.balance\",\"live.balance\"]}";

        webSocketHandler.handleTextMessage(session1, new TextMessage(subscribeMessage));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, times(2)).sendMessage(messageCaptor.capture());

        List<TextMessage> messages = messageCaptor.getAllValues();
        assertTrue(messages.get(0).getPayload().contains("\"type\":\"ack\""));
        assertTrue(messages.get(0).getPayload().contains("test.balance"));
        assertTrue(messages.get(1).getPayload().contains("\"type\":\"error\""));
        assertTrue(messages.get(1).getPayload().contains("live.balance"));
    }
}
