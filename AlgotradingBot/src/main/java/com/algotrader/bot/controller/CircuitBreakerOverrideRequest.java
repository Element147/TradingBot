package com.algotrader.bot.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CircuitBreakerOverrideRequest(
    @NotBlank
    String confirmationCode,
    @NotBlank
    @Size(min = 5, max = 255)
    String reason
) {}
