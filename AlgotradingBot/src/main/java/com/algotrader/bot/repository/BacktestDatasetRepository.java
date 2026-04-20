package com.algotrader.bot.repository;

import com.algotrader.bot.entity.BacktestDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BacktestDatasetRepository extends JpaRepository<BacktestDataset, Long> {
    List<BacktestDataset> findAllByOrderByUploadedAtDesc();
}
