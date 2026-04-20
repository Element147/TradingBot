package com.algotrader.bot.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaperOrderRequest(
    @NotBlank
    String symbol,
    @NotBlank
    String side,
    @NotNull
    @Positive
    BigDecimal quantity,
    @NotNull
    @Positive
    BigDecimal price,
    Boolean executeNow
) {
    public PaperOrderRequest {
        executeNow = executeNow == null ? Boolean.TRUE : executeNow;
    }
}
