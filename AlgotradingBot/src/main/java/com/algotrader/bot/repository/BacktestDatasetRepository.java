package com.algotrader.bot.repository;

import com.algotrader.bot.entity.BacktestDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BacktestDatasetRepository extends JpaRepository<BacktestDataset, Long> {
    List<BacktestDataset> findAllByOrderByUploadedAtDesc();
    Optional<BacktestDataset> findByChecksumSha256(String checksumSha256);
}
