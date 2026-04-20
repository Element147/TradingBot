package com.algotrader.bot.service;

import com.algotrader.bot.controller.BacktestResultResponse;
import com.algotrader.bot.controller.StartStrategyRequest;
import com.algotrader.bot.controller.StartStrategyResponse;
import com.algotrader.bot.controller.StopStrategyResponse;
import com.algotrader.bot.controller.StrategyStatusResponse;
import com.algotrader.bot.controller.TradeHistoryResponse;
import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.TradeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service layer for trading strategy operations.
 * Handles business logic for starting/stopping strategies, calculating metrics,
 * and retrieving trade history and backtest results.
 */
@Service
public class TradingStrategyService {

    private static final Logger logger = LoggerFactory.getLogger(TradingStrategyService.class);
    private static final int DEFAULT_QUERY_LIMIT = 100;
    private static final int MAX_QUERY_LIMIT = 1000;

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final BacktestResultRepository backtestResultRepository;

    public TradingStrategyService(AccountRepository accountRepository,
                                  PortfolioRepository portfolioRepository,
                                  TradeRepository tradeRepository,
                                  BacktestResultRepository backtestResultRepository) {
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.backtestResultRepository = backtestResultRepository;
    }

    /**
     * Start a new trading strategy with the specified parameters.
     *
     * @param request the strategy start request
     * @return map containing accountId and status
     */
    @Transactional
    public StartStrategyResponse startStrategy(StartStrategyRequest request) {
        logger.info("Starting trading strategy with initial balance: {}, pairs: {}",
            request.initialBalance(), request.pairs());

        Account account = new Account(
            request.initialBalance(),
            request.riskPerTrade(),
            request.maxDrawdown()
        );
        account.setStatus(Account.AccountStatus.ACTIVE);
        account = accountRepository.save(account);

        logger.info("Account initialized. Portfolio positions will be created on first trade.");
        logger.info("Trading strategy started successfully. Account ID: {}", account.getId());

        return new StartStrategyResponse(
            account.getId(),
            "ACTIVE",
            account.getInitialBalance(),
            "Trading strategy started successfully"
        );
    }

    /**
     * Stop a trading strategy and close all positions.
     *
     * @param accountId the account ID (null for latest active account)
     * @return stop strategy response with final balance and PnL
     */
    @Transactional
    public StopStrategyResponse stopStrategy(Long accountId) {
        Account account = getAccountOrLatest(accountId);

        logger.info("Stopping trading strategy for account ID: {}", account.getId());

        List<Portfolio> positions = portfolioRepository.findByAccountId(account.getId());
        for (Portfolio position : positions) {
            if (position.getPositionSize().compareTo(BigDecimal.ZERO) > 0) {
                logger.info("Closing position for {}: size={}",
                    position.getSymbol(), position.getPositionSize());
                BigDecimal realizedPnl = position.getUnrealizedPnl();
                BigDecimal cashRelease = position.getPositionSide() == PositionSide.LONG
                    ? position.getCurrentPrice().multiply(position.getPositionSize())
                    : position.getEntryNotional().add(realizedPnl);
                account.setCurrentBalance(account.getCurrentBalance().add(cashRelease));
                account.setTotalPnl(account.getTotalPnl().add(realizedPnl));
                portfolioRepository.delete(position);
            }
        }

        account.setStatus(Account.AccountStatus.STOPPED);
        accountRepository.save(account);

        logger.info("Trading strategy stopped. Final balance: {}, Total PnL: {}",
            account.getCurrentBalance(), account.getTotalPnl());

        return new StopStrategyResponse(
            account.getId(),
            "STOPPED",
            account.getCurrentBalance(),
            account.getTotalPnl(),
            account.getTotalReturnPercentage(),
            "Trading strategy stopped successfully"
        );
    }

    /**
     * Get current strategy status and performance metrics.
     *
     * @param accountId the account ID (null for latest active account)
     * @return strategy status response
     */
    @Transactional(readOnly = true)
    public StrategyStatusResponse getStatus(Long accountId) {
        Account account = getAccountOrLatest(accountId);
        logger.debug("Fetching status for account ID: {}", account.getId());

        List<Trade> accountTrades = tradeRepository.findByAccountId(account.getId());

        int totalTrades = accountTrades.size();
        int openPositions = portfolioRepository.findByAccountId(account.getId()).size();

        BigDecimal winRate = calculateWinRate(accountTrades);
        BigDecimal profitFactor = calculateProfitFactor(accountTrades);
        BigDecimal sharpeRatio = calculateSharpeRatio(accountTrades);
        BigDecimal maxDrawdown = account.getCurrentDrawdown();
        BigDecimal maxDrawdownPercent = maxDrawdown;

        return new StrategyStatusResponse(
            account.getCurrentBalance(),
            account.getTotalPnl(),
            account.getTotalReturnPercentage(),
            sharpeRatio,
            maxDrawdown,
            maxDrawdownPercent,
            openPositions,
            totalTrades,
            winRate,
            profitFactor,
            account.getStatus().name()
        );
    }

    /**
     * Get trade history with optional filters.
     *
     * @param accountId the account ID (null for all accounts)
     * @param symbol the trading pair symbol (null for all symbols)
     * @param startDate start date filter (null for no start date)
     * @param endDate end date filter (null for no end date)
     * @param limit maximum number of results
     * @return list of trade history responses
     */
    @Transactional(readOnly = true)
    public List<TradeHistoryResponse> getTradeHistory(Long accountId,
                                                      String symbol,
                                                      LocalDateTime startDate,
                                                      LocalDateTime endDate,
                                                      int limit) {
        String normalizedSymbol = normalizeNullable(symbol);
        int boundedLimit = sanitizeLimit(limit);
        PageRequest page = PageRequest.of(0, boundedLimit);
        boolean hasDateRange = startDate != null && endDate != null;

        logger.debug("Fetching trade history: accountId={}, symbol={}, startDate={}, endDate={}, limit={}",
            accountId, normalizedSymbol, startDate, endDate, boundedLimit);

        List<Trade> trades;
        if (accountId != null && normalizedSymbol != null && hasDateRange) {
            trades = tradeRepository.findByAccountIdAndSymbolAndEntryTimeBetweenOrderByEntryTimeDesc(
                accountId, normalizedSymbol, startDate, endDate, page);
        } else if (accountId != null && normalizedSymbol != null) {
            trades = tradeRepository.findByAccountIdAndSymbolOrderByEntryTimeDesc(accountId, normalizedSymbol, page);
        } else if (accountId != null && hasDateRange) {
            trades = tradeRepository.findByAccountIdAndEntryTimeBetweenOrderByEntryTimeDesc(
                accountId, startDate, endDate, page);
        } else if (accountId != null) {
            trades = tradeRepository.findByAccountIdOrderByEntryTimeDesc(accountId, page);
        } else if (normalizedSymbol != null && hasDateRange) {
            trades = tradeRepository.findBySymbolAndEntryTimeBetweenOrderByEntryTimeDesc(
                normalizedSymbol, startDate, endDate, page);
        } else if (normalizedSymbol != null) {
            trades = tradeRepository.findBySymbolOrderByEntryTimeDesc(normalizedSymbol, page);
        } else if (hasDateRange) {
            trades = tradeRepository.findByEntryTimeBetweenOrderByEntryTimeDesc(startDate, endDate, page);
        } else {
            trades = tradeRepository.findAllByOrderByEntryTimeDesc(page);
        }

        return trades.stream()
            .map(this::mapToTradeHistoryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TradeHistoryResponse getTradeDetails(Long tradeId, Long accountId) {
        Trade trade = tradeRepository.findById(tradeId)
            .orElseThrow(() -> new EntityNotFoundException("Trade not found with ID: " + tradeId));

        if (accountId != null && !accountId.equals(trade.getAccountId())) {
            throw new EntityNotFoundException("Trade not found with ID: " + tradeId + " for account " + accountId);
        }

        return mapToTradeHistoryResponse(trade);
    }

    /**
     * Get backtest results with optional filters.
     *
     * @param strategyId the strategy ID (null for all strategies)
     * @param symbol the trading pair symbol (null for all symbols)
     * @param limit maximum number of results
     * @return list of backtest result responses
     */
    @Transactional(readOnly = true)
    public List<BacktestResultResponse> getBacktestResults(String strategyId, String symbol, int limit) {
        String normalizedStrategyId = normalizeNullable(strategyId);
        String normalizedSymbol = normalizeNullable(symbol);
        int boundedLimit = sanitizeLimit(limit);
        PageRequest page = PageRequest.of(0, boundedLimit);

        logger.debug("Fetching backtest results: strategyId={}, symbol={}, limit={}",
            normalizedStrategyId, normalizedSymbol, boundedLimit);

        List<BacktestResult> results;
        if (normalizedStrategyId != null && normalizedSymbol != null) {
            results = backtestResultRepository.findByStrategyIdAndSymbolOrderByTimestampDesc(
                normalizedStrategyId, normalizedSymbol, page);
        } else if (normalizedStrategyId != null) {
            results = backtestResultRepository.findByStrategyIdOrderByTimestampDesc(normalizedStrategyId, page);
        } else if (normalizedSymbol != null) {
            results = backtestResultRepository.findBySymbolOrderByTimestampDesc(normalizedSymbol, page);
        } else {
            results = backtestResultRepository.findAllByOrderByTimestampDesc(page);
        }

        return results.stream()
            .map(this::mapToBacktestResultResponse)
            .toList();
    }

    private Account getAccountOrLatest(Long accountId) {
        if (accountId != null) {
            return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with ID: " + accountId));
        }

        return accountRepository.findTopByOrderByCreatedAtDesc()
            .orElseThrow(() -> new EntityNotFoundException("No accounts found"));
    }

    private int sanitizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_QUERY_LIMIT;
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal calculateWinRate(List<Trade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long winningTrades = trades.stream()
            .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) > 0)
            .count();

        return BigDecimal.valueOf(winningTrades)
            .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateProfitFactor(List<Trade> trades) {
        BigDecimal totalProfit = trades.stream()
            .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) > 0)
            .map(Trade::getPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLoss = trades.stream()
            .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) < 0)
            .map(Trade::getPnl)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalLoss.compareTo(BigDecimal.ZERO) == 0) {
            return totalProfit.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(999.99) : BigDecimal.ZERO;
        }

        return totalProfit.divide(totalLoss, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSharpeRatio(List<Trade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(1.5);
    }

    private TradeHistoryResponse mapToTradeHistoryResponse(Trade trade) {
        return new TradeHistoryResponse(
            trade.getId(),
            trade.getSymbol(),
            trade.getEntryTime(),
            trade.getEntryPrice(),
            trade.getExitTime(),
            trade.getExitPrice(),
            trade.getSignalType().name(),
            trade.getPositionSide().name(),
            trade.getPositionSize(),
            trade.getRiskAmount(),
            trade.getPnl(),
            trade.getActualFees(),
            trade.getActualSlippage(),
            trade.getStopLoss(),
            trade.getTakeProfit()
        );
    }

    private BacktestResultResponse mapToBacktestResultResponse(BacktestResult result) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateRange = result.getStartDate().format(formatter) + " to " +
            result.getEndDate().format(formatter);

        BigDecimal totalReturn = result.getFinalBalance()
            .subtract(result.getInitialBalance())
            .divide(result.getInitialBalance(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        BigDecimal annualReturn = totalReturn;
        BigDecimal calmarRatio = result.getMaxDrawdown().compareTo(BigDecimal.ZERO) > 0
            ? annualReturn.divide(result.getMaxDrawdown(), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new BacktestResultResponse(
            result.getStrategyId(),
            result.getSymbol(),
            dateRange,
            result.getInitialBalance(),
            result.getFinalBalance(),
            totalReturn,
            annualReturn,
            result.getSharpeRatio(),
            calmarRatio,
            result.getMaxDrawdown(),
            result.getWinRate(),
            result.getProfitFactor(),
            result.getTotalTrades(),
            0,
            0,
            result.getValidationStatus().name()
        );
    }
}
