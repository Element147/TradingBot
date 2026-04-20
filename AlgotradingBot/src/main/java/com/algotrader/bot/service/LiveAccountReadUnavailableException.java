package com.algotrader.bot.service;

public class LiveAccountReadUnavailableException extends RuntimeException {

    public LiveAccountReadUnavailableException(String message) {
        super(message);
    }
}
