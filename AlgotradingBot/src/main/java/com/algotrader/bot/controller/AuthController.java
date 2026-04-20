package com.algotrader.bot.controller;

import com.algotrader.bot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User authentication and token management endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login endpoint - authenticate user and return JWT tokens.
     * @param loginRequest the login credentials
     * @return authentication response with tokens
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with username and password, returns JWT access and refresh tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest loginRequest) {
        Map<String, Object> response = authService.login(
            loginRequest.username(),
            loginRequest.password()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint - invalidate JWT token.
     * @param authHeader the Authorization header containing the token
     * @return logout confirmation
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidate the current JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> logout(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (StringUtils.hasText(authHeader)) {
            String token = extractToken(authHeader);
            authService.logout(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    /**
     * Refresh token endpoint - get new access token using refresh token.
     * @param refreshTokenRequest the refresh token
     * @return new access token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get a new access token using a valid refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<Map<String, Object>> refreshToken(
        @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
        @RequestParam(value = "refreshToken", required = false) String refreshTokenParam
    ) {
        String refreshToken = refreshTokenRequest != null
            ? refreshTokenRequest.refreshToken()
            : refreshTokenParam;

        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    /**
     * Get current user endpoint - retrieve authenticated user information.
     * @param authHeader the Authorization header containing the token
     * @return current user information
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Retrieve information about the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, Object>> getCurrentUser(
        @RequestHeader("Authorization") String authHeader
    ) {
        String token = extractToken(authHeader);
        Map<String, Object> user = authService.getCurrentUser(token);
        return ResponseEntity.ok(user);
    }

    /**
     * Extract JWT token from Authorization header.
     * @param authHeader the Authorization header
     * @return JWT token
     */
    private String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Authorization header with Bearer token is required");
    }
}
