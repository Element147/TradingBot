package com.algotrader.bot.repository;

import com.algotrader.bot.entity.Trade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for Trade entity.
 * Provides CRUD operations and custom query methods for trade history retrieval.
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /**
     * Find all trades for a specific account.
     *
     * @param accountId the account ID
     * @return list of trades for the account
     */
    List<Trade> findByAccountId(Long accountId);
    List<Trade> findByAccountIdOrderByEntryTimeDesc(Long accountId, Pageable pageable);

    /**
     * Find all trades for a specific symbol.
     *
     * @param symbol the trading pair symbol (e.g., "BTC/USDT")
     * @return list of trades for the symbol
     */
    List<Trade> findBySymbol(String symbol);
    List<Trade> findBySymbolOrderByEntryTimeDesc(String symbol, Pageable pageable);

    /**
     * Find all trades for a specific account and symbol.
     *
     * @param accountId the account ID
     * @param symbol the trading pair symbol
     * @return list of trades matching both criteria
     */
    List<Trade> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Trade> findByAccountIdAndSymbolOrderByEntryTimeDesc(Long accountId, String symbol, Pageable pageable);

    /**
     * Find all trades within a specific time range.
     *
     * @param start the start date/time
     * @param end the end date/time
     * @return list of trades within the time range
     */
    List<Trade> findByEntryTimeBetween(LocalDateTime start, LocalDateTime end);
    List<Trade> findByEntryTimeBetweenOrderByEntryTimeDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find all trades for a specific symbol within a time range.
     *
     * @param symbol the trading pair symbol
     * @param start the start date/time
     * @param end the end date/time
     * @return list of trades matching the criteria
     */
    List<Trade> findBySymbolAndEntryTimeBetween(String symbol, LocalDateTime start, LocalDateTime end);
    List<Trade> findBySymbolAndEntryTimeBetweenOrderByEntryTimeDesc(
        String symbol,
        LocalDateTime start,
        LocalDateTime end,
        Pageable pageable
    );

    List<Trade> findByAccountIdAndEntryTimeBetweenOrderByEntryTimeDesc(
        Long accountId,
        LocalDateTime start,
        LocalDateTime end,
        Pageable pageable
    );

    List<Trade> findByAccountIdAndSymbolAndEntryTimeBetweenOrderByEntryTimeDesc(
        Long accountId,
        String symbol,
        LocalDateTime start,
        LocalDateTime end,
        Pageable pageable
    );

    /**
     * Find all trades for a specific account after a given time.
     *
     * @param accountId the account ID
     * @param startTime the start date/time
     * @return list of trades after the start time
     */
    List<Trade> findByAccountIdAndEntryTimeAfter(Long accountId, LocalDateTime startTime);

    /**
     * Find all completed trades (with exit time) for a specific account, ordered by exit time descending.
     *
     * @param accountId the account ID
     * @return list of completed trades ordered by exit time (most recent first)
     */
    List<Trade> findByAccountIdAndExitTimeNotNullOrderByExitTimeDesc(Long accountId);

    List<Trade> findAllByOrderByEntryTimeDesc(Pageable pageable);
}
