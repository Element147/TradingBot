package com.algotrader.bot.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExchangeConnectionProfileRequest(
    @NotBlank(message = "Connection name is required")
    @Size(max = 120, message = "Connection name must be 120 characters or less")
    String name,
    @NotBlank(message = "Exchange is required")
    @Size(max = 40, message = "Exchange must be 40 characters or less")
    String exchange,
    String apiKey,
    String apiSecret,
    Boolean testnet
) {}
