package com.algotrader.bot.service;

import com.algotrader.bot.controller.*;
import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for account and dashboard operations.
 * Handles balance retrieval, performance metrics calculation, and environment routing.
 */
@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final ExchangeIntegrationService exchangeIntegrationService;

    public AccountService(AccountRepository accountRepository,
                         PortfolioRepository portfolioRepository,
                         TradeRepository tradeRepository,
                         ExchangeIntegrationService exchangeIntegrationService) {
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.exchangeIntegrationService = exchangeIntegrationService;
    }

    /**
     * Get account balance based on environment mode.
     * Test mode: fetch from PostgreSQL
     * Live mode: fetch from exchange API (placeholder for now)
     *
     * @param environment "test" or "live"
     * @param accountId the account ID
     * @return balance response with total, available, locked, and asset breakdown
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String environment, Long accountId) {
        assertLiveReadsSupported(environment);
        Long resolvedAccountId = resolveAccountId(accountId);
        logger.info("Fetching balance for account {} in {} environment", accountId, environment);

        return getTestBalance(resolvedAccountId);
    }

    /**
     * Get balance from test database.
     */
    private BalanceResponse getTestBalance(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

        List<Portfolio> portfolios = portfolioRepository.findByAccountId(accountId);

        // Calculate total portfolio value
        BigDecimal portfolioValue = portfolios.stream()
                .map(Portfolio::getEquityContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBalance = account.getCurrentBalance().add(portfolioValue);
        BigDecimal availableBalance = account.getCurrentBalance();
        BigDecimal lockedBalance = portfolioValue;

        // Create asset breakdown
        List<BalanceResponse.AssetBalance> assets = new ArrayList<>();
        
        // Add USDT (cash balance)
        assets.add(new BalanceResponse.AssetBalance(
                "USDT",
                formatDecimal(availableBalance),
                formatDecimal(availableBalance)
        ));

        // Add portfolio positions
        for (Portfolio portfolio : portfolios) {
            assets.add(new BalanceResponse.AssetBalance(
                    portfolio.getSymbol() + " (" + portfolio.getPositionSide().name() + ")",
                    formatDecimal(portfolio.getPositionSize()),
                    formatDecimal(portfolio.getEquityContribution())
            ));
        }

        return new BalanceResponse(
                formatDecimal(totalBalance),
                formatDecimal(availableBalance),
                formatDecimal(lockedBalance),
                assets,
                LocalDateTime.now()
        );
    }

    /**
     * Get performance metrics for specified timeframe.
     *
     * @param environment "test" or "live"
     * @param accountId the account ID
     * @param timeframe "today", "week", "month", "all-time"
     * @return performance metrics
     */
    @Transactional(readOnly = true)
    public PerformanceResponse getPerformance(String environment, Long accountId, String timeframe) {
        assertLiveReadsSupported(environment);
        Long resolvedAccountId = resolveAccountId(accountId);
        logger.info("Fetching performance for account {} in {} environment, timeframe: {}", 
                   resolvedAccountId, environment, timeframe);

        Account account = accountRepository.findById(resolvedAccountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + resolvedAccountId));

        LocalDateTime startTime = calculateStartTime(timeframe);
        List<Trade> trades = tradeRepository.findByAccountIdAndEntryTimeAfter(resolvedAccountId, startTime);

        // Calculate metrics
        BigDecimal totalPnL = trades.stream()
                .filter(t -> t.getPnl() != null)
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long winningTrades = trades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();

        BigDecimal winRate = trades.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(winningTrades)
                        .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        BigDecimal pnlPercentage = account.getInitialBalance().compareTo(BigDecimal.ZERO) == 0 ?
                BigDecimal.ZERO :
                totalPnL.divide(account.getInitialBalance(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        // Calculate cash ratio
        List<Portfolio> portfolios = portfolioRepository.findByAccountId(resolvedAccountId);
        BigDecimal portfolioValue = portfolios.stream()
                .map(Portfolio::getEquityContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = account.getCurrentBalance().add(portfolioValue);
        BigDecimal cashRatio = totalValue.compareTo(BigDecimal.ZERO) == 0 ?
                BigDecimal.ZERO :
                account.getCurrentBalance()
                        .divide(totalValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        return new PerformanceResponse(
                formatDecimal(totalPnL),
                formatDecimal(pnlPercentage),
                formatDecimal(winRate),
                trades.size(),
                formatDecimal(cashRatio)
        );
    }

    /**
     * Get open positions with unrealized P&L.
     *
     * @param environment "test" or "live"
     * @param accountId the account ID
     * @return list of open positions
     */
    @Transactional(readOnly = true)
    public List<OpenPositionResponse> getOpenPositions(String environment, Long accountId) {
        assertLiveReadsSupported(environment);
        Long resolvedAccountId = resolveAccountId(accountId);
        logger.info("Fetching open positions for account {} in {} environment", resolvedAccountId, environment);

        List<Portfolio> portfolios = portfolioRepository.findByAccountId(resolvedAccountId);

        return portfolios.stream()
                .map(p -> new OpenPositionResponse(
                        p.getId(),
                        p.getSymbol(),
                        p.getPositionSide().name(),
                        formatDecimal(p.getAverageEntryPrice()),
                        formatDecimal(p.getCurrentPrice()),
                        formatDecimal(p.getPositionSize()),
                        formatDecimal(p.getUnrealizedPnl()),
                        formatDecimal(p.getUnrealizedPnlPercentage()),
                        p.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get recent completed trades.
     *
     * @param environment "test" or "live"
     * @param accountId the account ID
     * @param limit maximum number of trades to return
     * @return list of recent trades
     */
    @Transactional(readOnly = true)
    public List<RecentTradeResponse> getRecentTrades(String environment, Long accountId, int limit) {
        assertLiveReadsSupported(environment);
        Long resolvedAccountId = resolveAccountId(accountId);
        logger.info("Fetching recent {} trades for account {} in {} environment", 
                   limit, resolvedAccountId, environment);

        List<Trade> trades = tradeRepository.findByAccountIdAndExitTimeNotNullOrderByExitTimeDesc(resolvedAccountId);

        return trades.stream()
                .limit(limit)
                .map(t -> {
                    BigDecimal pnlPercentage = t.getEntryPrice().compareTo(BigDecimal.ZERO) == 0 ?
                            BigDecimal.ZERO :
                            t.getPnl().divide(t.getEntryPrice().multiply(t.getPositionSize()), 
                                            4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));

                    return new RecentTradeResponse(
                            t.getId(),
                            t.getSymbol(),
                            t.getPositionSide().name(),
                            formatDecimal(t.getEntryPrice()),
                            formatDecimal(t.getExitPrice()),
                            formatDecimal(t.getPositionSize()),
                            formatDecimal(t.getPnl()),
                            formatDecimal(pnlPercentage),
                            t.getEntryTime(),
                            t.getExitTime()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate start time based on timeframe.
     */
    private LocalDateTime calculateStartTime(String timeframe) {
        LocalDateTime now = LocalDateTime.now();
        return switch (timeframe.toLowerCase()) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "week" -> now.minusWeeks(1);
            case "month" -> now.minusMonths(1);
            case "all-time", "all" -> LocalDateTime.of(2000, 1, 1, 0, 0);
            default -> now.minusMonths(1);
        };
    }

    /**
     * Format BigDecimal to string with 8 decimal places.
     */
    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "0.00000000";
        }
        return value.setScale(8, RoundingMode.HALF_UP).toPlainString();
    }

    private Long resolveAccountId(Long requestedAccountId) {
        if (requestedAccountId != null) {
            return requestedAccountId;
        }

        return accountRepository.findTopByOrderByCreatedAtDesc()
                .map(Account::getId)
                .orElseThrow(() -> new RuntimeException("No trading account found"));
    }

    private void assertLiveReadsSupported(String environment) {
        if (EnvironmentRequestResolver.LIVE.equalsIgnoreCase(environment)) {
            throw new LiveAccountReadUnavailableException(exchangeIntegrationService.getLiveAccountReadUnavailableReason());
        }
    }
}
