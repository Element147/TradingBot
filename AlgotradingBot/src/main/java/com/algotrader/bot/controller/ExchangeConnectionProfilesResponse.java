package com.algotrader.bot.controller;

import java.util.List;

public record ExchangeConnectionProfilesResponse(
    List<ExchangeConnectionProfileResponse> connections,
    String activeConnectionId
) {}
