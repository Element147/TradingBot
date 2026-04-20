package com.algotrader.bot.controller;

import com.algotrader.bot.service.TradingStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API controller for trading strategy operations.
 * Provides endpoints for starting/stopping strategies, monitoring performance,
 * viewing trade history, and accessing backtest results.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Trading Strategy", description = "Endpoints for managing and monitoring algorithmic trading strategies")
public class TradingStrategyController {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingStrategyController.class);
    
    private final TradingStrategyService tradingStrategyService;

    public TradingStrategyController(TradingStrategyService tradingStrategyService) {
        this.tradingStrategyService = tradingStrategyService;
    }
    
    /**
     * Start a new trading strategy.
     * 
     * POST /api/strategy/start
     * 
     * @param request the strategy start request with initial balance, pairs, and risk parameters
     * @return response containing accountId and status
     */
    @Operation(
        summary = "Start a new trading strategy",
        description = "Initializes a new trading account with specified parameters and starts the trading strategy. " +
                     "Creates an account with the given initial balance, risk parameters, and trading pairs."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Strategy started successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StartStrategyResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @PostMapping("/strategy/start")
    public ResponseEntity<StartStrategyResponse> startStrategy(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Strategy configuration including initial balance, trading pairs, and risk parameters",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = StartStrategyRequest.class),
                    examples = @ExampleObject(
                        value = "{\"initialBalance\": 1000.00, \"pairs\": [\"BTC/USDT\", \"ETH/USDT\"], \"riskPerTrade\": 0.02, \"maxDrawdown\": 0.25}"
                    )
                )
            )
            @Valid @RequestBody StartStrategyRequest request) {
        
        logger.info("Received request to start trading strategy: {}", request);
        
        StartStrategyResponse response = tradingStrategyService.startStrategy(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get current strategy status and performance metrics.
     * 
     * GET /api/strategy/status
     * 
     * @param accountId optional account ID (defaults to latest active account)
     * @return strategy status response with current metrics
     */
    @Operation(
        summary = "Get strategy status and performance metrics",
        description = "Retrieves current status and performance metrics for a trading account including PnL, " +
                     "Sharpe ratio, drawdown, open positions, and win rate. If no accountId is provided, " +
                     "returns status for the most recently created account."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Status retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StrategyStatusResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Account not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/strategy/status")
    public ResponseEntity<StrategyStatusResponse> getStatus(
            @Parameter(description = "Account ID (optional, defaults to latest account)", example = "1")
            @RequestParam(required = false) Long accountId) {
        
        logger.info("Received request to get strategy status for account: {}", accountId);
        
        StrategyStatusResponse response = tradingStrategyService.getStatus(accountId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop a trading strategy and close all positions.
     * 
     * POST /api/strategy/stop
     * 
     * @param accountId optional account ID (defaults to latest active account)
     * @return response containing final balance and PnL
     */
    @Operation(
        summary = "Stop trading strategy",
        description = "Stops the trading strategy for the specified account, closes all open positions, " +
                     "and calculates final PnL. If no accountId is provided, stops the most recently created account."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Strategy stopped successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = StopStrategyResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Account not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @PostMapping("/strategy/stop")
    public ResponseEntity<StopStrategyResponse> stopStrategy(
            @Parameter(description = "Account ID (optional, defaults to latest account)", example = "1")
            @RequestParam(required = false) Long accountId) {
        
        logger.info("Received request to stop trading strategy for account: {}", accountId);
        
        StopStrategyResponse response = tradingStrategyService.stopStrategy(accountId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get trade history with optional filters.
     * 
     * GET /api/trades/history
     * 
     * @param accountId optional account ID filter
     * @param symbol optional trading pair symbol filter (e.g., "BTC/USDT")
     * @param startDate optional start date filter
     * @param endDate optional end date filter
     * @param limit maximum number of results (default: 100, max: 1000)
     * @return list of trade history responses
     */
    @Operation(
        summary = "Get trade history",
        description = "Retrieves historical trades with optional filters for account, symbol, and date range. " +
                     "Returns detailed information about each trade including entry/exit prices, PnL, fees, and slippage."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Trade history retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TradeHistoryResponse.class)
            )
        )
    })
    @GetMapping("/trades/history")
    public ResponseEntity<List<TradeHistoryResponse>> getTradeHistory(
            @Parameter(description = "Filter by account ID", example = "1")
            @RequestParam(required = false) Long accountId,
            @Parameter(description = "Filter by trading pair symbol", example = "BTC/USDT")
            @RequestParam(required = false) String symbol,
            @Parameter(description = "Filter trades after this date (ISO 8601 format)", example = "2024-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Filter trades before this date (ISO 8601 format)", example = "2024-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Maximum number of results to return (max: 1000)", example = "100")
            @RequestParam(defaultValue = "100") int limit) {
        
        // Enforce max limit
        if (limit > 1000) {
            limit = 1000;
        }
        
        logger.info("Received request to get trade history: accountId={}, symbol={}, startDate={}, endDate={}, limit={}", 
                   accountId, symbol, startDate, endDate, limit);
        
        List<TradeHistoryResponse> response = tradingStrategyService.getTradeHistory(
            accountId, symbol, startDate, endDate, limit);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get one trade detail record",
        description = "Retrieves one historical trade by ID, with optional account scoping for safer lookup flows."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Trade detail retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TradeHistoryResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Trade not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/trades/{tradeId}")
    public ResponseEntity<TradeHistoryResponse> getTradeDetails(
            @Parameter(description = "Trade ID", example = "1")
            @PathVariable Long tradeId,
            @Parameter(description = "Optional account scope for the trade lookup", example = "1")
            @RequestParam(required = false) Long accountId) {

        logger.info("Received request to get trade details: tradeId={}, accountId={}", tradeId, accountId);

        return ResponseEntity.ok(tradingStrategyService.getTradeDetails(tradeId, accountId));
    }
    
    /**
     * Get backtest results with optional filters.
     * 
     * GET /api/backtest/results
     * 
     * @param strategyId optional strategy ID filter
     * @param symbol optional trading pair symbol filter
     * @param limit maximum number of results (default: 10)
     * @return list of backtest result responses
     */
    @Operation(
        summary = "Get backtest results",
        description = "Retrieves historical backtest results with optional filters for strategy and symbol. " +
                     "Returns comprehensive performance metrics including Sharpe ratio, profit factor, win rate, and validation status."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Backtest results retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BacktestResultResponse.class)
            )
        )
    })
    @GetMapping("/backtest/results")
    public ResponseEntity<List<BacktestResultResponse>> getBacktestResults(
            @Parameter(description = "Filter by strategy ID", example = "bollinger-bands-v1")
            @RequestParam(required = false) String strategyId,
            @Parameter(description = "Filter by trading pair symbol", example = "BTC/USDT")
            @RequestParam(required = false) String symbol,
            @Parameter(description = "Maximum number of results to return", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        
        logger.info("Received request to get backtest results: strategyId={}, symbol={}, limit={}", 
                   strategyId, symbol, limit);
        
        List<BacktestResultResponse> response = tradingStrategyService.getBacktestResults(
            strategyId, symbol, limit);
        
        return ResponseEntity.ok(response);
    }
}
