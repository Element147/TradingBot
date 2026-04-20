package com.algotrader.bot.controller;

public record SystemInfoResponse(
    String applicationVersion,
    String lastDeploymentDate,
    String databaseStatus
) {}
