package com.algotrader.bot.repository;

import com.algotrader.bot.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    List<StrategyConfig> findAllByOrderByNameAsc();
}
