package com.algotrader.bot.repository;

import com.algotrader.bot.entity.StrategyConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyConfigVersionRepository extends JpaRepository<StrategyConfigVersion, Long> {
    List<StrategyConfigVersion> findByStrategyConfigIdOrderByVersionNumberDesc(Long strategyConfigId);

    long countByStrategyConfigId(Long strategyConfigId);

    Optional<StrategyConfigVersion> findFirstByStrategyConfigIdOrderByVersionNumberDesc(Long strategyConfigId);
}
