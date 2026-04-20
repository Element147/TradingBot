package com.algotrader.bot.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Historical trade information")
public record TradeHistoryResponse(
    @Schema(description = "Trade ID", example = "1")
    Long id,
    @Schema(description = "Trading pair symbol", example = "BTC/USDT")
    String pair,
    @Schema(description = "Trade entry timestamp", example = "2024-01-15T10:30:00")
    LocalDateTime entryTime,
    @Schema(description = "Entry price in USDT", example = "50000.00")
    BigDecimal entryPrice,
    @Schema(description = "Trade exit timestamp", example = "2024-01-15T14:30:00")
    LocalDateTime exitTime,
    @Schema(description = "Exit price in USDT", example = "51000.00")
    BigDecimal exitPrice,
    @Schema(description = "Trade signal type", example = "BUY", allowableValues = {"BUY", "SELL", "SHORT", "COVER", "HOLD"})
    String signal,
    @Schema(description = "Position direction", example = "LONG", allowableValues = {"LONG", "SHORT"})
    String positionSide,
    @Schema(description = "Position size (quantity)", example = "0.01")
    BigDecimal positionSize,
    @Schema(description = "Risk amount in USDT", example = "20.00")
    BigDecimal riskAmount,
    @Schema(description = "Profit/loss in USDT", example = "10.00")
    BigDecimal pnl,
    @Schema(description = "Actual fees paid in USDT", example = "5.00")
    BigDecimal feesActual,
    @Schema(description = "Actual slippage in USDT", example = "1.50")
    BigDecimal slippageActual,
    @Schema(description = "Stop loss price", example = "49000.00")
    BigDecimal stopLoss,
    @Schema(description = "Take profit price", example = "51000.00")
    BigDecimal takeProfit
) {}
