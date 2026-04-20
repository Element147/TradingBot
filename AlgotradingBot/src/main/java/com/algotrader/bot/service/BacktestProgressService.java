package com.algotrader.bot.service;

import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class BacktestProgressService {

    private final WebSocketEventPublisher webSocketEventPublisher;

    public BacktestProgressService(WebSocketEventPublisher webSocketEventPublisher) {
        this.webSocketEventPublisher = webSocketEventPublisher;
    }

    public void publish(BacktestResult result) {
        webSocketEventPublisher.publishBacktestProgress(
            "test",
            result.getId(),
            result.getStrategyId(),
            result.getDatasetName(),
            result.getExperimentName(),
            result.getSymbol(),
            result.getTimeframe(),
            result.getExecutionStatus().name(),
            result.getValidationStatus().name(),
            result.getFeesBps(),
            result.getSlippageBps(),
            result.getTimestamp(),
            result.getInitialBalance(),
            result.getFinalBalance(),
            result.getExecutionStage().name(),
            result.getProgressPercent(),
            result.getProcessedCandles(),
            result.getTotalCandles(),
            result.getCurrentDataTimestamp(),
            result.getLastProgressAt(),
            result.getStatusMessage(),
            result.getStartedAt(),
            result.getCompletedAt(),
            result.getErrorMessage()
        );
    }
}
