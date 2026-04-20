package com.algotrader.bot.websocket;

import com.algotrader.bot.security.JwtTokenProvider;
import com.algotrader.bot.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthHandshakeInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthService authService;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    private WebSocketAuthHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthHandshakeInterceptor(jwtTokenProvider, authService);
    }

    @Test
    void beforeHandshakeAcceptsValidAccessTokenAndEnvironment() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws?token=valid-token&env=test"));
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(authService.isTokenBlacklisted("valid-token")).thenReturn(false);
        when(jwtTokenProvider.getUsernameFromToken("valid-token")).thenReturn("ws-user");
        when(jwtTokenProvider.getRoleFromToken("valid-token")).thenReturn("TRADER");

        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                mock(org.springframework.web.socket.WebSocketHandler.class),
                attributes
        );

        assertTrue(accepted);
        assertEquals(Boolean.TRUE, attributes.get(WebSocketHandler.AUTHENTICATED_ATTRIBUTE));
        assertEquals("test", attributes.get(WebSocketHandler.ENVIRONMENT_ATTRIBUTE));
        assertEquals("ws-user", attributes.get(WebSocketHandler.USERNAME_ATTRIBUTE));
        assertEquals("TRADER", attributes.get(WebSocketHandler.ROLE_ATTRIBUTE));
        verify(response, never()).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshakeRejectsMissingToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws?env=test"));

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                mock(org.springframework.web.socket.WebSocketHandler.class),
                new HashMap<>()
        );

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshakeRejectsBlacklistedToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws?token=revoked-token&env=test"));
        when(jwtTokenProvider.validateToken("revoked-token")).thenReturn(true);
        when(authService.isTokenBlacklisted("revoked-token")).thenReturn(true);

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                mock(org.springframework.web.socket.WebSocketHandler.class),
                new HashMap<>()
        );

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshakeRejectsRefreshTokenWithoutRoleClaim() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws?token=refresh-token&env=test"));
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(authService.isTokenBlacklisted("refresh-token")).thenReturn(false);
        when(jwtTokenProvider.getUsernameFromToken("refresh-token")).thenReturn("ws-user");
        when(jwtTokenProvider.getRoleFromToken("refresh-token")).thenReturn(null);

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                mock(org.springframework.web.socket.WebSocketHandler.class),
                new HashMap<>()
        );

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshakeRejectsUnsupportedEnvironment() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws?token=valid-token&env=prod"));
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(authService.isTokenBlacklisted("valid-token")).thenReturn(false);

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                mock(org.springframework.web.socket.WebSocketHandler.class),
                new HashMap<>()
        );

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }
}
