package com.algotrader.bot.controller;

public record ExchangeConnectionTestRequest(
    String exchange,
    String apiKey,
    String apiSecret,
    Boolean testnet
) {}
