package com.algotrader.bot.service;

import com.algotrader.bot.backtest.strategy.BacktestStrategyDefinition;
import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.controller.StrategyActionResponse;
import com.algotrader.bot.controller.StrategyConfigHistoryResponse;
import com.algotrader.bot.controller.StrategyDetailsResponse;
import com.algotrader.bot.controller.UpdateStrategyConfigRequest;
import com.algotrader.bot.entity.StrategyConfig;
import com.algotrader.bot.entity.StrategyConfigVersion;
import com.algotrader.bot.repository.StrategyConfigRepository;
import com.algotrader.bot.repository.StrategyConfigVersionRepository;
import com.algotrader.bot.websocket.WebSocketEventPublisher;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StrategyManagementService {

    private static final Logger logger = LoggerFactory.getLogger(StrategyManagementService.class);

    private final StrategyConfigRepository strategyConfigRepository;
    private final StrategyConfigVersionRepository strategyConfigVersionRepository;
    private final BacktestStrategyRegistry backtestStrategyRegistry;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final OperatorAuditService operatorAuditService;

    public StrategyManagementService(StrategyConfigRepository strategyConfigRepository,
                                     StrategyConfigVersionRepository strategyConfigVersionRepository,
                                     BacktestStrategyRegistry backtestStrategyRegistry,
                                     WebSocketEventPublisher webSocketEventPublisher,
                                     OperatorAuditService operatorAuditService) {
        this.strategyConfigRepository = strategyConfigRepository;
        this.strategyConfigVersionRepository = strategyConfigVersionRepository;
        this.backtestStrategyRegistry = backtestStrategyRegistry;
        this.webSocketEventPublisher = webSocketEventPublisher;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional
    public List<StrategyDetailsResponse> listStrategies() {
        ensureStrategyCatalogCoverage();
        ensureVersionHistoryForExistingStrategies();

        return strategyConfigRepository.findAllByOrderByNameAsc().stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional
    public StrategyActionResponse startStrategy(Long strategyId) {
        StrategyConfig strategy = getById(strategyId);

        strategy.setStatus(StrategyConfig.StrategyStatus.RUNNING);
        strategy.setStartedAt(LocalDateTime.now());
        strategyConfigRepository.save(strategy);

        webSocketEventPublisher.publishStrategyStatus(
            "paper",
            String.valueOf(strategy.getId()),
            strategy.getStatus().name(),
            "Strategy started"
        );

        operatorAuditService.recordSuccess(
            "STRATEGY_STARTED",
            "paper",
            "STRATEGY",
            String.valueOf(strategy.getId()),
            strategy.getName()
        );

        return new StrategyActionResponse(strategy.getId(), strategy.getStatus().name(), "Strategy started in paper mode");
    }

    @Transactional
    public StrategyActionResponse stopStrategy(Long strategyId) {
        StrategyConfig strategy = getById(strategyId);

        strategy.setStatus(StrategyConfig.StrategyStatus.STOPPED);
        strategy.setStoppedAt(LocalDateTime.now());
        strategyConfigRepository.save(strategy);

        webSocketEventPublisher.publishStrategyStatus(
            "paper",
            String.valueOf(strategy.getId()),
            strategy.getStatus().name(),
            "Strategy stopped"
        );

        operatorAuditService.recordSuccess(
            "STRATEGY_STOPPED",
            "paper",
            "STRATEGY",
            String.valueOf(strategy.getId()),
            strategy.getName()
        );

        return new StrategyActionResponse(strategy.getId(), strategy.getStatus().name(), "Strategy stopped");
    }

    @Transactional
    public StrategyDetailsResponse updateStrategyConfig(Long strategyId, UpdateStrategyConfigRequest request) {
        StrategyConfig strategy = getById(strategyId);

        if (request.maxPositionSize().compareTo(request.minPositionSize()) < 0) {
            throw new IllegalArgumentException("Max position size must be greater than or equal to min position size");
        }

        ensureVersionHistoryForStrategy(strategy);

        if (StrategyConfig.StrategyStatus.RUNNING.equals(strategy.getStatus())) {
            strategy.setStatus(StrategyConfig.StrategyStatus.STOPPED);
            strategy.setStoppedAt(LocalDateTime.now());
        }

        String changeReason = buildChangeReason(strategy, request);

        strategy.setSymbol(request.symbol());
        strategy.setTimeframe(request.timeframe());
        strategy.setRiskPerTrade(request.riskPerTrade());
        strategy.setMinPositionSize(request.minPositionSize());
        strategy.setMaxPositionSize(request.maxPositionSize());
        strategy.setShortSellingEnabled(request.shortSellingEnabled());

        StrategyConfig saved = strategyConfigRepository.save(strategy);
        recordVersion(saved, changeReason);

        webSocketEventPublisher.publishStrategyStatus(
            "paper",
            String.valueOf(saved.getId()),
            saved.getStatus().name(),
            "Strategy configuration updated"
        );

        operatorAuditService.recordSuccess(
            "STRATEGY_CONFIG_UPDATED",
            "paper",
            "STRATEGY",
            String.valueOf(saved.getId()),
            changeReason
        );

        return mapToResponse(saved);
    }

    @Transactional
    public List<StrategyConfigHistoryResponse> getStrategyConfigHistory(Long strategyId) {
        StrategyConfig strategy = getById(strategyId);
        ensureVersionHistoryForStrategy(strategy);

        return strategyConfigVersionRepository.findByStrategyConfigIdOrderByVersionNumberDesc(strategyId).stream()
            .map(version -> new StrategyConfigHistoryResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getChangeReason(),
                version.getSymbol(),
                version.getTimeframe(),
                version.getRiskPerTrade(),
                version.getMinPositionSize(),
                version.getMaxPositionSize(),
                version.getStatus(),
                version.getPaperMode(),
                version.getShortSellingEnabled(),
                version.getChangedAt()
            ))
            .toList();
    }

    private StrategyConfig getById(Long strategyId) {
        return strategyConfigRepository.findById(strategyId)
            .orElseThrow(() -> new EntityNotFoundException("Strategy not found: " + strategyId));
    }

    private StrategyDetailsResponse mapToResponse(StrategyConfig strategy) {
        StrategyConfigVersion latestVersion = strategyConfigVersionRepository
            .findFirstByStrategyConfigIdOrderByVersionNumberDesc(strategy.getId())
            .orElse(null);

        return new StrategyDetailsResponse(
            strategy.getId(),
            strategy.getName(),
            canonicalTypeOrOriginal(strategy.getType()),
            strategy.getStatus().name(),
            strategy.getSymbol(),
            strategy.getTimeframe(),
            strategy.getRiskPerTrade(),
            strategy.getMinPositionSize(),
            strategy.getMaxPositionSize(),
            strategy.getProfitLoss(),
            strategy.getTradeCount(),
            strategy.getCurrentDrawdown(),
            strategy.getPaperMode(),
            strategy.getShortSellingEnabled(),
            latestVersion == null ? 0 : latestVersion.getVersionNumber(),
            latestVersion == null ? null : latestVersion.getChangedAt()
        );
    }

    private void ensureStrategyCatalogCoverage() {
        List<StrategyConfig> existingStrategies = strategyConfigRepository.findAll();
        Set<String> coveredTypes = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        boolean normalizedExistingRows = false;

        for (StrategyConfig strategy : existingStrategies) {
            usedNames.add(strategy.getName());
            String canonicalType = canonicalTypeOrOriginal(strategy.getType());
            coveredTypes.add(canonicalType);
            if (!canonicalType.equals(strategy.getType())) {
                strategy.setType(canonicalType);
                normalizedExistingRows = true;
            }
        }

        if (normalizedExistingRows) {
            logger.info("Normalizing persisted strategy types to canonical backtest catalog identifiers");
            strategyConfigRepository.saveAll(existingStrategies);
        }

        List<StrategyConfig> missingStrategies = backtestStrategyRegistry.getDefinitions().stream()
            .filter(definition -> !coveredTypes.contains(definition.type().name()))
            .map(definition -> createSeedStrategy(definition, usedNames))
            .toList();

        if (missingStrategies.isEmpty()) {
            return;
        }

        logger.info("Seeding {} missing strategy catalog entries for paper-mode management", missingStrategies.size());
        List<StrategyConfig> savedStrategies = strategyConfigRepository.saveAll(missingStrategies);
        savedStrategies.forEach(strategy -> recordVersion(strategy, "Seeded strategy catalog entry"));
    }

    private void ensureVersionHistoryForExistingStrategies() {
        strategyConfigRepository.findAll().forEach(this::ensureVersionHistoryForStrategy);
    }

    private void ensureVersionHistoryForStrategy(StrategyConfig strategy) {
        if (strategyConfigVersionRepository.countByStrategyConfigId(strategy.getId()) > 0) {
            return;
        }

        recordVersion(strategy, "Captured existing strategy configuration baseline");
    }

    private void recordVersion(StrategyConfig strategy, String changeReason) {
        int nextVersion = (int) strategyConfigVersionRepository.countByStrategyConfigId(strategy.getId()) + 1;
        strategyConfigVersionRepository.save(StrategyConfigVersion.fromStrategy(strategy, nextVersion, changeReason));
    }

    private String buildChangeReason(StrategyConfig strategy, UpdateStrategyConfigRequest request) {
        List<String> changedFields = new ArrayList<>();
        if (!strategy.getSymbol().equals(request.symbol())) {
            changedFields.add("symbol");
        }
        if (!strategy.getTimeframe().equals(request.timeframe())) {
            changedFields.add("timeframe");
        }
        if (strategy.getRiskPerTrade().compareTo(request.riskPerTrade()) != 0) {
            changedFields.add("riskPerTrade");
        }
        if (strategy.getMinPositionSize().compareTo(request.minPositionSize()) != 0) {
            changedFields.add("minPositionSize");
        }
        if (strategy.getMaxPositionSize().compareTo(request.maxPositionSize()) != 0) {
            changedFields.add("maxPositionSize");
        }
        if (!strategy.getShortSellingEnabled().equals(request.shortSellingEnabled())) {
            changedFields.add("shortSellingEnabled");
        }
        if (changedFields.isEmpty()) {
            return "No parameter changes detected; configuration was re-saved.";
        }

        return "Updated " + String.join(", ", changedFields);
    }

    private String canonicalTypeOrOriginal(String type) {
        try {
            return BacktestAlgorithmType.from(type).name();
        } catch (IllegalArgumentException ignored) {
            return type;
        }
    }

    private StrategyConfig createSeedStrategy(BacktestStrategyDefinition definition, Set<String> usedNames) {
        StrategySeedPreset preset = seedPreset(definition.type());
        String name = resolveSeedName(definition.label(), usedNames);
        return new StrategyConfig(
            name,
            definition.type().name(),
            preset.symbol(),
            preset.timeframe(),
            preset.riskPerTrade(),
            preset.minPositionSize(),
            preset.maxPositionSize()
        );
    }

    private String resolveSeedName(String label, Set<String> usedNames) {
        String candidate = label;
        if (!usedNames.contains(candidate)) {
            usedNames.add(candidate);
            return candidate;
        }

        int suffix = 1;
        while (usedNames.contains(candidate + " Paper " + suffix)) {
            suffix++;
        }

        String resolved = candidate + " Paper " + suffix;
        usedNames.add(resolved);
        return resolved;
    }

    private StrategySeedPreset seedPreset(BacktestAlgorithmType type) {
        return switch (type) {
            case BUY_AND_HOLD -> new StrategySeedPreset("BTC/USDT", "1d", new BigDecimal("0.02"),
                new BigDecimal("10.00"), new BigDecimal("100.00"));
            case DUAL_MOMENTUM_ROTATION -> new StrategySeedPreset("BTC/USDT", "1d", new BigDecimal("0.02"),
                new BigDecimal("25.00"), new BigDecimal("150.00"));
            case VOLATILITY_MANAGED_DONCHIAN_BREAKOUT -> new StrategySeedPreset("BTC/USDT", "4h",
                new BigDecimal("0.02"), new BigDecimal("20.00"), new BigDecimal("120.00"));
            case TREND_PULLBACK_CONTINUATION -> new StrategySeedPreset("BTC/USDT", "4h",
                new BigDecimal("0.02"), new BigDecimal("15.00"), new BigDecimal("100.00"));
            case REGIME_FILTERED_MEAN_REVERSION -> new StrategySeedPreset("BTC/USDT", "1h",
                new BigDecimal("0.015"), new BigDecimal("10.00"), new BigDecimal("80.00"));
            case TREND_FIRST_ADAPTIVE_ENSEMBLE -> new StrategySeedPreset("BTC/USDT", "4h",
                new BigDecimal("0.015"), new BigDecimal("20.00"), new BigDecimal("120.00"));
            case SMA_CROSSOVER -> new StrategySeedPreset("BTC/USDT", "4h", new BigDecimal("0.02"),
                new BigDecimal("15.00"), new BigDecimal("110.00"));
            case BOLLINGER_BANDS -> new StrategySeedPreset("BTC/USDT", "1h", new BigDecimal("0.015"),
                new BigDecimal("10.00"), new BigDecimal("90.00"));
            case ICHIMOKU_TREND -> new StrategySeedPreset("BTC/USDT", "1d", new BigDecimal("0.015"),
                new BigDecimal("20.00"), new BigDecimal("120.00"));
            case OPENING_RANGE_VWAP_BREAKOUT -> new StrategySeedPreset("SPY", "15m", new BigDecimal("0.01"),
                new BigDecimal("10.00"), new BigDecimal("75.00"));
            case VWAP_PULLBACK_CONTINUATION -> new StrategySeedPreset("SPY", "15m", new BigDecimal("0.01"),
                new BigDecimal("10.00"), new BigDecimal("75.00"));
            case EXHAUSTION_REVERSAL_FADE -> new StrategySeedPreset("SPY", "15m", new BigDecimal("0.01"),
                new BigDecimal("10.00"), new BigDecimal("60.00"));
            case MULTI_TIMEFRAME_EMA_ADX_PULLBACK -> new StrategySeedPreset("SPY", "1h", new BigDecimal("0.01"),
                new BigDecimal("15.00"), new BigDecimal("90.00"));
            case SQUEEZE_BREAKOUT_REGIME_CONFIRMATION -> new StrategySeedPreset("SPY", "1h", new BigDecimal("0.01"),
                new BigDecimal("15.00"), new BigDecimal("85.00"));
            case RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER -> new StrategySeedPreset("SPY", "1h",
                new BigDecimal("0.01"), new BigDecimal("15.00"), new BigDecimal("80.00"));
        };
    }

    private record StrategySeedPreset(
        String symbol,
        String timeframe,
        BigDecimal riskPerTrade,
        BigDecimal minPositionSize,
        BigDecimal maxPositionSize
    ) {
    }
}
