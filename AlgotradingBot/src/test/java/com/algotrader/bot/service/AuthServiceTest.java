package com.algotrader.bot.service;

import com.algotrader.bot.entity.User;
import com.algotrader.bot.repository.AuthTokenRevocationRepository;
import com.algotrader.bot.repository.UserRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthTokenRevocationRepository authTokenRevocationRepository;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPasswordHash("$2a$10$hashedpassword");
        testUser.setEmail("test@example.com");
        testUser.setRole(User.Role.TRADER);
        testUser.setEnabled(true);
    }

    @Test
    void testLogin_Success() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken("testuser", "TRADER")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("testuser")).thenReturn("refresh-token");

        // Act
        Map<String, Object> response = authService.login("testuser", "password123");

        // Assert
        assertNotNull(response);
        assertEquals("access-token", response.get("accessToken"));
        assertEquals("refresh-token", response.get("refreshToken"));
        assertEquals("Bearer", response.get("tokenType"));
        assertEquals(3600, response.get("expiresIn"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.get("user");
        assertEquals("testuser", user.get("username"));
        assertEquals("TRADER", user.get("role"));

        verify(userRepository).findByUsername("testuser");
        verify(passwordEncoder).matches("password123", testUser.getPasswordHash());
        verify(jwtTokenProvider).generateToken("testuser", "TRADER");
        verify(jwtTokenProvider).generateRefreshToken("testuser");
    }

    @Test
    void testLogin_UserNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> 
            authService.login("nonexistent", "password123")
        );

        verify(userRepository).findByUsername("nonexistent");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testLogin_InvalidPassword() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> 
            authService.login("testuser", "wrongpassword")
        );

        verify(userRepository).findByUsername("testuser");
        verify(passwordEncoder).matches("wrongpassword", testUser.getPasswordHash());
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyString());
    }

    @Test
    void testLogin_DisabledUser() {
        // Arrange
        testUser.setEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> 
            authService.login("testuser", "password123")
        );

        verify(userRepository).findByUsername("testuser");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testLogout_Success() {
        // Arrange
        String token = "valid-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getExpirationFromToken(token)).thenReturn(Instant.parse("2026-03-20T00:00:00Z"));
        when(authTokenRevocationRepository.existsByTokenHash(anyString())).thenReturn(false, true);

        // Act
        authService.logout(token);

        // Assert
        assertTrue(authService.isTokenBlacklisted(token));
        verify(jwtTokenProvider).validateToken(token);
        verify(jwtTokenProvider).getExpirationFromToken(token);
        verify(authTokenRevocationRepository).save(any());
    }

    @Test
    void testRefreshToken_Success() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateToken("testuser", "TRADER")).thenReturn("new-access-token");

        // Act
        Map<String, Object> response = authService.refreshToken(refreshToken);

        // Assert
        assertNotNull(response);
        assertEquals("new-access-token", response.get("accessToken"));
        assertEquals("Bearer", response.get("tokenType"));
        assertEquals(3600, response.get("expiresIn"));

        verify(jwtTokenProvider).validateToken(refreshToken);
        verify(jwtTokenProvider).getUsernameFromToken(refreshToken);
        verify(userRepository).findByUsername("testuser");
        verify(jwtTokenProvider).generateToken("testuser", "TRADER");
    }

    @Test
    void testRefreshToken_InvalidToken() {
        // Arrange
        String refreshToken = "invalid-token";
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> 
            authService.refreshToken(refreshToken)
        );

        verify(jwtTokenProvider).validateToken(refreshToken);
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
    }

    @Test
    void testRefreshToken_BlacklistedToken() {
        // Arrange
        String refreshToken = "blacklisted-token";
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getExpirationFromToken(refreshToken)).thenReturn(Instant.parse("2026-03-20T00:00:00Z"));
        when(authTokenRevocationRepository.existsByTokenHash(anyString())).thenReturn(false, true);
        authService.logout(refreshToken); // Blacklist the token

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> 
            authService.refreshToken(refreshToken)
        );

        verify(jwtTokenProvider, atLeastOnce()).validateToken(refreshToken);
    }

    @Test
    void testIsTokenBlacklisted_BlankToken() {
        assertFalse(authService.isTokenBlacklisted(" "));
        verifyNoInteractions(authTokenRevocationRepository);
    }

    @Test
    void testGetCurrentUser_Success() {
        // Arrange
        String token = "valid-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        Map<String, Object> user = authService.getCurrentUser(token);

        // Assert
        assertNotNull(user);
        assertEquals(1L, user.get("id"));
        assertEquals("testuser", user.get("username"));
        assertEquals("test@example.com", user.get("email"));
        assertEquals("TRADER", user.get("role"));

        verify(jwtTokenProvider).validateToken(token);
        verify(jwtTokenProvider).getUsernameFromToken(token);
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void testGetCurrentUser_InvalidToken() {
        // Arrange
        String token = "invalid-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> 
            authService.getCurrentUser(token)
        );

        verify(jwtTokenProvider).validateToken(token);
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
    }

    @Test
    void testGetCurrentUser_BlacklistedToken() {
        // Arrange
        String token = "revoked-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(authTokenRevocationRepository.existsByTokenHash(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () ->
            authService.getCurrentUser(token)
        );

        verify(jwtTokenProvider).validateToken(token);
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
    }

    @Test
    void testCreateUser_Success() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });

        // Act
        User createdUser = authService.createUser("newuser", "password123", "new@example.com", User.Role.TRADER);

        // Assert
        assertNotNull(createdUser);
        assertEquals(2L, createdUser.getId());
        assertEquals("newuser", createdUser.getUsername());
        assertEquals("new@example.com", createdUser.getEmail());
        assertEquals(User.Role.TRADER, createdUser.getRole());
        assertTrue(createdUser.getEnabled());

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("new@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testCreateUser_UsernameExists() {
        // Arrange
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            authService.createUser("existinguser", "password123", "new@example.com", User.Role.TRADER)
        );

        verify(userRepository).existsByUsername("existinguser");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateUser_EmailExists() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            authService.createUser("newuser", "password123", "existing@example.com", User.Role.TRADER)
        );

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("existing@example.com");
        verify(userRepository, never()).save(any(User.class));
    }
}
