package com.algotrader.bot.repository;

import com.algotrader.bot.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Account entity.
 * Provides CRUD operations and custom query methods for account management.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Find all accounts with a specific status.
     *
     * @param status the account status (ACTIVE, STOPPED, CIRCUIT_BREAKER_TRIGGERED)
     * @return list of accounts with the specified status
     */
    List<Account> findByStatus(Account.AccountStatus status);

    /**
     * Find the most recently created account.
     * Useful for getting the latest active trading account.
     *
     * @return optional containing the latest account, or empty if no accounts exist
     */
    Optional<Account> findTopByOrderByCreatedAtDesc();
}
