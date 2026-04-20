package com.algotrader.bot.repository;

import com.algotrader.bot.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Portfolio entity.
 * Provides CRUD operations and custom query methods for portfolio position management.
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    /**
     * Find all portfolio positions for a specific account.
     *
     * @param accountId the account ID
     * @return list of portfolio positions for the account
     */
    List<Portfolio> findByAccountId(Long accountId);

    /**
     * Find a specific portfolio position by account and symbol.
     *
     * @param accountId the account ID
     * @param symbol the trading pair symbol
     * @return optional containing the portfolio position, or empty if not found
     */
    Optional<Portfolio> findByAccountIdAndSymbol(Long accountId, String symbol);

    /**
     * Find all portfolio positions for a specific symbol across all accounts.
     *
     * @param symbol the trading pair symbol
     * @return list of portfolio positions for the symbol
     */
    List<Portfolio> findBySymbol(String symbol);
}
