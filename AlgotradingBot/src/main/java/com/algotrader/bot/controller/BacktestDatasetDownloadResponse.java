package com.algotrader.bot.controller;

public record BacktestDatasetDownloadResponse(
    String originalFilename,
    String checksumSha256,
    String schemaVersion,
    byte[] csvData,
    String exportSource
) {
}
