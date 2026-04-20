package com.algotrader.bot.controller;

import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MarketDataImportJobRequest(
    @NotBlank(message = "Provider is required")
    String providerId,
    @NotNull(message = "Asset type is required")
    MarketDataAssetType assetType,
    @NotEmpty(message = "At least one symbol is required")
    List<@NotBlank(message = "Symbols cannot be blank") String> symbols,
    @NotBlank(message = "Timeframe is required")
    String timeframe,
    @NotNull(message = "Start date is required")
    LocalDate startDate,
    @NotNull(message = "End date is required")
    LocalDate endDate,
    @Size(max = 100, message = "Dataset name must be <= 100 characters")
    String datasetName,
    Boolean adjusted,
    Boolean regularSessionOnly
) {
}
