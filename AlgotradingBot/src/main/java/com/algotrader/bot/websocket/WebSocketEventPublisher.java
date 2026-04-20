package com.algotrader.bot.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing WebSocket events.
 * Provides convenience methods for common event types.
 */
@Service
public class WebSocketEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventPublisher.class);

    private final WebSocketHandler webSocketHandler;

    public WebSocketEventPublisher(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Publish balance updated event.
     *
     * @param environment "test" or "live"
     * @param total total balance
     * @param available available balance
     * @param locked locked balance
     */
    public void publishBalanceUpdated(String environment, String total, String available, String locked) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("available", available);
        data.put("locked", locked);

        Map<String, Object> event = createEvent("balance.updated", environment, data);
        String channel = environment + ".balance";
        
        logger.debug("Publishing balance.updated event to channel: {}", channel);
        webSocketHandler.publishEvent(channel, event);
    }

    /**
     * Publish trade executed event.
     *
     * @param environment "test" or "live"
     * @param tradeId trade ID
     * @param symbol trading pair symbol
     * @param side BUY or SELL
     * @param price execution price
     * @param quantity trade quantity
     * @param pnl profit/loss (null for entry)
     */
    public void publishTradeExecuted(String environment, Long tradeId, String symbol, 
                                     String side, String price, String quantity, String pnl) {
        Map<String, Object> data = new HashMap<>();
        data.put("tradeId", tradeId);
        data.put("symbol", symbol);
        data.put("side", side);
        data.put("price", price);
        data.put("quantity", quantity);
        if (pnl != null) {
            data.put("pnl", pnl);
        }

        Map<String, Object> event = createEvent("trade.executed", environment, data);
        String channel = environment + ".trades";
        
        logger.debug("Publishing trade.executed event to channel: {}", channel);
        webSocketHandler.publishEvent(channel, event);
    }

    /**
     * Publish position updated event.
     *
     * @param environment "test" or "live"
     * @param positionId position ID
     * @param symbol trading pair symbol
     * @param currentPrice current market price
     * @param unrealizedPnL unrealized profit/loss
     */
    public void publishPositionUpdated(String environment, Long positionId, String symbol,
                                       String currentPrice, String unrealizedPnL) {
        Map<String, Object> data = new HashMap<>();
        data.put("positionId", positionId);
        data.put("symbol", symbol);
        data.put("currentPrice", currentPrice);
        data.put("unrealizedPnL", unrealizedPnL);

        Map<String, Object> event = createEvent("position.updated", environment, data);
        String channel = environment + ".positions";
        
        logger.debug("Publishing position.updated event to channel: {}", channel);
        webSocketHandler.publishEvent(channel, event);
    }

    /**
     * Publish strategy status event.
     *
     * @param environment "test" or "live"
     * @param strategyId strategy ID
     * @param status strategy status (RUNNING, STOPPED, ERROR)
     * @param message optional status message
     */
    public void publishStrategyStatus(String environment, String strategyId, 
                                      String status, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("strategyId", strategyId);
        data.put("status", status);
        if (message != null) {
            data.put("message", message);
        }

        Map<String, Object> event = createEvent("strategy.status", environment, data);
        String channel = environment + ".strategies";
        
        logger.debug("Publishing strategy.status event to channel: {}", channel);
        webSocketHandler.publishEvent(channel, event);
    }

    public void publishBacktestProgress(String environment,
                                        Long backtestId,
                                        String strategyId,
                                        String datasetName,
                                        String experimentName,
                                        String symbol,
                                        String timeframe,
                                        String executionStatus,
                                        String validationStatus,
                                        Integer feesBps,
                                        Integer slippageBps,
                                        LocalDateTime timestamp,
                                        BigDecimal initialBalance,
                                        BigDecimal finalBalance,
                                        String executionStage,
                                        Integer progressPercent,
                                        Integer processedCandles,
                                        Integer totalCandles,
                                        LocalDateTime currentDataTimestamp,
                                        LocalDateTime lastProgressAt,
                                        String statusMessage,
                                        LocalDateTime startedAt,
                                        LocalDateTime completedAt,
                                        String errorMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("backtestId", backtestId);
        data.put("strategyId", strategyId);
        data.put("datasetName", datasetName);
        data.put("experimentName", experimentName);
        data.put("symbol", symbol);
        data.put("timeframe", timeframe);
        data.put("executionStatus", executionStatus);
        data.put("validationStatus", validationStatus);
        data.put("feesBps", feesBps);
        data.put("slippageBps", slippageBps);
        data.put("timestamp", toIso(timestamp));
        data.put("initialBalance", initialBalance);
        data.put("finalBalance", finalBalance);
        data.put("executionStage", executionStage);
        data.put("progressPercent", progressPercent);
        data.put("processedCandles", processedCandles);
        data.put("totalCandles", totalCandles);
        data.put("currentDataTimestamp", toIso(currentDataTimestamp));
        data.put("lastProgressAt", toIso(lastProgressAt));
        data.put("statusMessage", statusMessage);
        data.put("startedAt", toIso(startedAt));
        data.put("completedAt", toIso(completedAt));
        data.put("errorMessage", errorMessage);

        Map<String, Object> event = createEvent("backtest.progress", environment, data);
        String channel = environment + ".backtests";

        logger.debug("Publishing backtest.progress event to channel: {}", channel);
        webSocketHandler.publishEvent(channel, event);
    }

    public void publishMarketDataImportProgress(String environment,
                                                Long jobId,
                                                String providerId,
                                                String providerLabel,
                                                String assetType,
                                                String datasetName,
                                                String symbolsCsv,
                                                String timeframe,
                                                String startDate,
                                                String endDate,
                                                boolean adjusted,
                                                boolean regularSessionOnly,
                                                String status,
                                                String statusMessage,
                                                LocalDateTime nextRetryAt,
                                                Integer currentSymbolIndex,
                                                Integer totalSymbols,
                                                String currentSymbol,
                                                Integer importedRowCount,
                                                Long datasetId,
                                                boolean datasetReady,
                                                LocalDateTime currentChunkStart,
                                                Integer attemptCount,
                                                LocalDateTime createdAt,
                                                LocalDateTime updatedAt,
                                                LocalDateTime startedAt,
                                                LocalDateTime completedAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", jobId);
        data.put("providerId", providerId);
        data.put("providerLabel", providerLabel);
        data.put("assetType", assetType);
        data.put("datasetName", datasetName);
        data.put("symbolsCsv", symbolsCsv);
        data.put("timeframe", timeframe);
        data.put("startDate", startDate);
        data.put("endDate", endDate);
        data.put("adjusted", adjusted);
        data.put("regularSessionOnly", regularSessionOnly);
        data.put("status", status);
        data.put("statusMessage", statusMessage);
        data.put("nextRetryAt", toIso(nextRetryAt));
        data.put("currentSymbolIndex", currentSymbolIndex);
        data.put("totalSymbols", totalSymbols);
        data.put("currentSymbol", currentSymbol);
        data.put("importedRowCount", importedRowCount);
        data.put("datasetId", datasetId);
        data.put("datasetReady", datasetReady);
        data.put("currentChunkStart", toIso(currentChunkStart));
        data.put("attemptCount", attemptCount);
        data.put("createdAt", toIso(createdAt));
        data.put("updatedAt", toIso(updatedAt));
        data.put("startedAt", toIso(startedAt));
        data.put("completedAt", toIso(completedAt));

        Map<String, Object> event = createEvent("marketData.import.progress", environment, data);
        String channel = environment + ".marketData";

        logger.debug("Publishing marketData.import.progress event to channel: {}", channel);
        webSocketHandler.publishEvent(channel, event);
    }

    /**
     * Create event structure with type, environment, timestamp, and data.
     */
    private Map<String, Object> createEvent(String type, String environment, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("environment", environment);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("data", data);
        return event;
    }

    private String toIso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
