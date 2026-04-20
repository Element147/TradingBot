package com.algotrader.bot.service;

import com.algotrader.bot.entity.AuthTokenRevocation;
import com.algotrader.bot.entity.User;
import com.algotrader.bot.repository.AuthTokenRevocationRepository;
import com.algotrader.bot.repository.UserRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for authentication operations including login, logout, and token management.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthTokenRevocationRepository authTokenRevocationRepository;

    public AuthService(UserRepository userRepository, 
                      PasswordEncoder passwordEncoder,
                      JwtTokenProvider jwtTokenProvider,
                      AuthTokenRevocationRepository authTokenRevocationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authTokenRevocationRepository = authTokenRevocationRepository;
    }

    /**
     * Authenticate user and generate tokens.
     * @param username the username
     * @param password the password
     * @return map containing access token, refresh token, and user info
     */
    @Transactional(readOnly = true)
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.getEnabled()) {
            throw new BadCredentialsException("User account is disabled");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String accessToken = jwtTokenProvider.generateToken(username, user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 3600); // 1 hour in seconds
        response.put("user", mapUserToResponse(user));

        return response;
    }

    /**
     * Logout user by invalidating token.
     * @param token the JWT token to invalidate
     */
    @Transactional
    public void logout(String token) {
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String tokenHash = hashToken(token);
            if (!authTokenRevocationRepository.existsByTokenHash(tokenHash)) {
                AuthTokenRevocation revocation = new AuthTokenRevocation();
                revocation.setTokenHash(tokenHash);
                revocation.setExpiresAt(LocalDateTime.ofInstant(jwtTokenProvider.getExpirationFromToken(token), ZoneOffset.UTC));
                revocation.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
                authTokenRevocationRepository.save(revocation);
            }

            cleanupRevocations();
        }
    }

    /**
     * Refresh access token using refresh token.
     * @param refreshToken the refresh token
     * @return map containing new access token
     */
    @Transactional(readOnly = true)
    public Map<String, Object> refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (isTokenBlacklisted(refreshToken)) {
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.getEnabled()) {
            throw new BadCredentialsException("User account is disabled");
        }

        String newAccessToken = jwtTokenProvider.generateToken(username, user.getRole().name());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 3600);

        return response;
    }

    /**
     * Get current user information from token.
     * @param token the JWT token
     * @return user information map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentUser(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BadCredentialsException("Invalid token");
        }

        if (isTokenBlacklisted(token)) {
            throw new BadCredentialsException("Token has been revoked");
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return mapUserToResponse(user);
    }

    /**
     * Check if token is blacklisted.
     * @param token the token to check
     * @return true if blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        return StringUtils.hasText(token) && authTokenRevocationRepository.existsByTokenHash(hashToken(token));
    }

    /**
     * Create a new user (for testing/admin purposes).
     * @param username the username
     * @param password the plain password
     * @param email the email
     * @param role the user role
     * @return created user
     */
    @Transactional
    public User createUser(String username, String password, String email, User.Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);

        return userRepository.save(user);
    }

    /**
     * Map user entity to response object.
     * @param user the user entity
     * @return user response map
     */
    private Map<String, Object> mapUserToResponse(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole().name());
        return userMap;
    }

    private void cleanupRevocations() {
        authTokenRevocationRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available for token hashing", e);
        }
    }
}
