package com.algotrader.bot.controller;

import com.algotrader.bot.service.AccountService;
import com.algotrader.bot.service.EnvironmentRequestResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for account and dashboard operations.
 * Provides endpoints for balance, performance metrics, positions, and recent trades.
 * Supports environment-aware routing (test vs live).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Account", description = "Account and dashboard operations")
@SecurityRequirement(name = "bearer-jwt")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);
    private final AccountService accountService;
    private final EnvironmentRequestResolver environmentRequestResolver;

    public AccountController(AccountService accountService, EnvironmentRequestResolver environmentRequestResolver) {
        this.accountService = accountService;
        this.environmentRequestResolver = environmentRequestResolver;
    }

    /**
     * Get account balance with asset breakdown.
     * Supports environment-aware routing via query parameter.
     *
     * @param env environment mode: "test" or "live" (default: "test")
     * @param accountId optional account ID (defaults to latest account)
     * @return balance response with total, available, locked, and asset breakdown
     */
    @GetMapping("/account/balance")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get account balance",
            description = "Returns account balance with asset breakdown. Requests are environment-aware, but live-mode reads currently return a capability error until live exchange balances are wired."
    )
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Environment mode: test or live")
            @RequestParam(required = false) String env,
            @Parameter(description = "Environment mode header: test or live")
            @RequestHeader(name = "X-Environment", required = false) String headerEnvironment,
            @Parameter(description = "Execution context: research, forward-test, paper, or live")
            @RequestParam(required = false) String context,
            @Parameter(description = "Execution context header: research, forward-test, paper, or live")
            @RequestHeader(name = "X-Execution-Context", required = false) String headerExecutionContext,
            @Parameter(description = "Account ID (optional, defaults to latest account)")
            @RequestParam(required = false) Long accountId) {

        String environment = environmentRequestResolver.resolve(
            env,
            headerEnvironment,
            context,
            headerExecutionContext
        );
        logger.info("GET /api/account/balance - environment: {}, accountId: {}", environment, accountId);

        BalanceResponse balance = accountService.getBalance(environment, accountId);
        return ResponseEntity.ok(balance);
    }

    /**
     * Get performance metrics for specified timeframe.
     *
     * @param env environment mode: "test" or "live" (default: "test")
     * @param timeframe timeframe: "today", "week", "month", "all-time" (default: "month")
     * @param accountId optional account ID (defaults to latest account)
     * @return performance metrics including P&L, win rate, trade count, cash ratio
     */
    @GetMapping("/account/performance")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get performance metrics",
            description = "Returns performance metrics for specified timeframe: today, week, month, or all-time. Requests are environment-aware, but live-mode reads currently return a capability error until live exchange performance reads are wired."
    )
    public ResponseEntity<PerformanceResponse> getPerformance(
            @Parameter(description = "Environment mode: test or live")
            @RequestParam(required = false) String env,
            @Parameter(description = "Environment mode header: test or live")
            @RequestHeader(name = "X-Environment", required = false) String headerEnvironment,
            @Parameter(description = "Execution context: research, forward-test, paper, or live")
            @RequestParam(required = false) String context,
            @Parameter(description = "Execution context header: research, forward-test, paper, or live")
            @RequestHeader(name = "X-Execution-Context", required = false) String headerExecutionContext,
            @Parameter(description = "Timeframe: today, week, month, all-time")
            @RequestParam(defaultValue = "month") String timeframe,
            @Parameter(description = "Account ID (optional, defaults to latest account)")
            @RequestParam(required = false) Long accountId) {

        String environment = environmentRequestResolver.resolve(
            env,
            headerEnvironment,
            context,
            headerExecutionContext
        );
        logger.info("GET /api/account/performance - environment: {}, timeframe: {}, accountId: {}",
                environment, timeframe, accountId);

        PerformanceResponse performance = accountService.getPerformance(environment, accountId, timeframe);
        return ResponseEntity.ok(performance);
    }

    /**
     * Get open positions with unrealized P&L.
     *
     * @param env environment mode: "test" or "live" (default: "test")
     * @param accountId optional account ID (defaults to latest account)
     * @return list of open positions
     */
    @GetMapping("/positions/open")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get open positions",
            description = "Returns all open positions with unrealized P&L. Requests are environment-aware, but live-mode reads currently return a capability error until live exchange positions are wired."
    )
    public ResponseEntity<List<OpenPositionResponse>> getOpenPositions(
            @Parameter(description = "Environment mode: test or live")
            @RequestParam(required = false) String env,
            @Parameter(description = "Environment mode header: test or live")
            @RequestHeader(name = "X-Environment", required = false) String headerEnvironment,
            @Parameter(description = "Execution context: research, forward-test, paper, or live")
            @RequestParam(required = false) String context,
            @Parameter(description = "Execution context header: research, forward-test, paper, or live")
            @RequestHeader(name = "X-Execution-Context", required = false) String headerExecutionContext,
            @Parameter(description = "Account ID (optional, defaults to latest account)")
            @RequestParam(required = false) Long accountId) {

        String environment = environmentRequestResolver.resolve(
            env,
            headerEnvironment,
            context,
            headerExecutionContext
        );
        logger.info("GET /api/positions/open - environment: {}, accountId: {}", environment, accountId);

        List<OpenPositionResponse> positions = accountService.getOpenPositions(environment, accountId);
        return ResponseEntity.ok(positions);
    }

    /**
     * Get recent completed trades.
     *
     * @param env environment mode: "test" or "live" (default: "test")
     * @param limit maximum number of trades to return (default: 10)
     * @param accountId optional account ID (defaults to latest account)
     * @return list of recent completed trades
     */
    @GetMapping("/trades/recent")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get recent trades",
            description = "Returns recent completed trades with entry/exit details and P&L. Requests are environment-aware, but live-mode reads currently return a capability error until live exchange trade history is wired."
    )
    public ResponseEntity<List<RecentTradeResponse>> getRecentTrades(
            @Parameter(description = "Environment mode: test or live")
            @RequestParam(required = false) String env,
            @Parameter(description = "Environment mode header: test or live")
            @RequestHeader(name = "X-Environment", required = false) String headerEnvironment,
            @Parameter(description = "Execution context: research, forward-test, paper, or live")
            @RequestParam(required = false) String context,
            @Parameter(description = "Execution context header: research, forward-test, paper, or live")
            @RequestHeader(name = "X-Execution-Context", required = false) String headerExecutionContext,
            @Parameter(description = "Maximum number of trades to return")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Account ID (optional, defaults to latest account)")
            @RequestParam(required = false) Long accountId) {

        String environment = environmentRequestResolver.resolve(
            env,
            headerEnvironment,
            context,
            headerExecutionContext
        );
        logger.info("GET /api/trades/recent - environment: {}, limit: {}, accountId: {}", environment, limit, accountId);

        List<RecentTradeResponse> trades = accountService.getRecentTrades(environment, accountId, limit);
        return ResponseEntity.ok(trades);
    }
}
