package com.algotrader.bot.risk;

import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Risk management component that enforces circuit breakers and drawdown limits.
 * Protects capital by stopping trading when performance degrades.
 * 
 * Circuit Breaker Rules:
 * - Sharpe ratio < 0.8 for 5 consecutive days → STOP
 * - Drawdown > 25% → STOP
 * - Account status STOPPED → STOP
 */
@Component
public class RiskManager {

    private static final Logger logger = LoggerFactory.getLogger(RiskManager.class);

    // Circuit breaker thresholds
    private static final BigDecimal MIN_SHARPE_RATIO = new BigDecimal("0.8");
    private static final int CONSECUTIVE_DAYS_THRESHOLD = 5;
    private static final int ROLLING_WINDOW_DAYS = 30;
    
    // Scale for BigDecimal calculations
    private static final int SCALE = 8;

    /**
     * Check if circuit breaker should be triggered based on Sharpe ratio.
     * 
     * @param account The trading account
     * @param recentTrades List of recent trades (last 30 days)
     * @return RiskCheckResult indicating if trading should stop
     */
    public RiskCheckResult checkCircuitBreaker(Account account, List<Trade> recentTrades) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        // Calculate rolling Sharpe ratio
        BigDecimal sharpeRatio = calculateSharpeRatio(recentTrades);
        
        // Check if Sharpe ratio is below threshold
        if (sharpeRatio.compareTo(MIN_SHARPE_RATIO) < 0) {
            // Check if this has been true for consecutive days
            int consecutiveDays = countConsecutiveLowSharpeDays(recentTrades);
            
            if (consecutiveDays >= CONSECUTIVE_DAYS_THRESHOLD) {
                String reason = String.format(
                    "Circuit breaker triggered: Sharpe ratio %.2f below threshold %.2f for %d consecutive days",
                    sharpeRatio, MIN_SHARPE_RATIO, consecutiveDays
                );
                
                logger.warn("Circuit breaker triggered for account {}: {}", account.getId(), reason);
                
                return new RiskCheckResult(
                    false,
                    reason,
                    account.getCurrentDrawdown(),
                    sharpeRatio
                );
            }
        }

        return new RiskCheckResult(
            true,
            "Circuit breaker check passed",
            account.getCurrentDrawdown(),
            sharpeRatio
        );
    }

    /**
     * Check if drawdown limit has been exceeded.
     * 
     * @param account The trading account
     * @return RiskCheckResult indicating if trading should stop
     */
    public RiskCheckResult checkDrawdown(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        BigDecimal currentDrawdown = account.getCurrentDrawdown();
        BigDecimal maxDrawdownLimit = account.getMaxDrawdownLimit();

        if (currentDrawdown.compareTo(maxDrawdownLimit) > 0) {
            String reason = String.format(
                "Drawdown limit exceeded: current %.2f%% > limit %.2f%%",
                currentDrawdown, maxDrawdownLimit
            );
            
            logger.warn("Drawdown limit exceeded for account {}: {}", account.getId(), reason);
            
            return new RiskCheckResult(
                false,
                reason,
                currentDrawdown,
                BigDecimal.ZERO
            );
        }

        return new RiskCheckResult(
            true,
            "Drawdown check passed",
            currentDrawdown,
            BigDecimal.ZERO
        );
    }

    /**
     * Comprehensive check if trading is allowed for the account.
     * Checks account status, circuit breaker, and drawdown limit.
     * 
     * @param account The trading account
     * @param recentTrades List of recent trades (last 30 days)
     * @return RiskCheckResult indicating if trading is allowed
     */
    public RiskCheckResult canTrade(Account account, List<Trade> recentTrades) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        // Check account status
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            String reason = "Account status is " + account.getStatus() + ", trading not allowed";
            logger.info("Trading not allowed for account {}: {}", account.getId(), reason);
            
            return new RiskCheckResult(
                false,
                reason,
                account.getCurrentDrawdown(),
                BigDecimal.ZERO
            );
        }

        // Check drawdown limit
        RiskCheckResult drawdownCheck = checkDrawdown(account);
        if (!drawdownCheck.isCanTrade()) {
            return drawdownCheck;
        }

        // Check circuit breaker
        RiskCheckResult circuitBreakerCheck = checkCircuitBreaker(account, recentTrades);
        if (!circuitBreakerCheck.isCanTrade()) {
            return circuitBreakerCheck;
        }

        logger.debug("All risk checks passed for account {}", account.getId());
        
        return new RiskCheckResult(
            true,
            "All risk checks passed",
            account.getCurrentDrawdown(),
            circuitBreakerCheck.getSharpeRatio()
        );
    }

    /**
     * Update account status and log the reason.
     * 
     * @param account The trading account
     * @param newStatus The new account status
     * @param reason The reason for the status change
     */
    public void updateAccountStatus(Account account, Account.AccountStatus newStatus, String reason) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }

        Account.AccountStatus oldStatus = account.getStatus();
        account.setStatus(newStatus);

        // Log status change with structured JSON
        logger.info("Account status changed: accountId={}, oldStatus={}, newStatus={}, reason={}",
            account.getId(), oldStatus, newStatus, reason);
    }

    /**
     * Calculate Sharpe ratio from recent trades.
     * Sharpe Ratio = (Mean Return - Risk-Free Rate) / Standard Deviation of Returns
     * 
     * For simplicity, we assume risk-free rate = 0 and calculate based on trade PnL.
     * 
     * @param trades List of trades
     * @return Sharpe ratio as BigDecimal
     */
    private BigDecimal calculateSharpeRatio(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Filter trades with PnL (closed trades only)
        List<Trade> closedTrades = trades.stream()
            .filter(t -> t.getPnl() != null)
            .collect(Collectors.toList());

        if (closedTrades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate mean return
        BigDecimal totalPnl = closedTrades.stream()
            .map(Trade::getPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal meanReturn = totalPnl.divide(
            BigDecimal.valueOf(closedTrades.size()),
            SCALE,
            RoundingMode.HALF_UP
        );

        // Calculate standard deviation
        BigDecimal variance = closedTrades.stream()
            .map(t -> t.getPnl().subtract(meanReturn).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(closedTrades.size()), SCALE, RoundingMode.HALF_UP);

        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        // Avoid division by zero
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Sharpe ratio = mean return / standard deviation
        return meanReturn.divide(stdDev, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Count consecutive days with low Sharpe ratio.
     * 
     * @param trades List of recent trades
     * @return Number of consecutive days with Sharpe < threshold
     */
    private int countConsecutiveLowSharpeDays(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0;
        }

        // Group trades by day and calculate daily Sharpe ratio
        LocalDateTime now = LocalDateTime.now();
        int consecutiveDays = 0;

        for (int i = 0; i < CONSECUTIVE_DAYS_THRESHOLD; i++) {
            LocalDateTime dayStart = now.minusDays(i + 1).truncatedTo(ChronoUnit.DAYS);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            List<Trade> dayTrades = trades.stream()
                .filter(t -> t.getEntryTime() != null)
                .filter(t -> !t.getEntryTime().isBefore(dayStart) && t.getEntryTime().isBefore(dayEnd))
                .collect(Collectors.toList());

            if (dayTrades.isEmpty()) {
                break; // No trades for this day, stop counting
            }

            BigDecimal dailySharpe = calculateSharpeRatio(dayTrades);
            
            if (dailySharpe.compareTo(MIN_SHARPE_RATIO) < 0) {
                consecutiveDays++;
            } else {
                break; // Consecutive streak broken
            }
        }

        return consecutiveDays;
    }
}
