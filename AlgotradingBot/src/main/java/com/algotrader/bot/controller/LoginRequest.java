package com.algotrader.bot.controller;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for login endpoint.
 */
public record LoginRequest(
    @NotBlank(message = "Username is required")
    String username,
    @NotBlank(message = "Password is required")
    String password,
    boolean rememberMe
) {
    public LoginRequest(String username, String password) {
        this(username, password, false);
    }
}
