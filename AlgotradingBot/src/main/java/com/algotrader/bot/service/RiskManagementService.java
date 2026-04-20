package com.algotrader.bot.service;

import com.algotrader.bot.controller.*;
import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.RiskAlert;
import com.algotrader.bot.entity.RiskConfig;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.RiskAlertRepository;
import com.algotrader.bot.repository.RiskConfigRepository;
import com.algotrader.bot.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiskManagementService {

    private static final String OVERRIDE_CODE = "OVERRIDE_PAPER_ONLY";

    private final RiskConfigRepository riskConfigRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;
    private final OperatorAuditService operatorAuditService;

    public RiskManagementService(RiskConfigRepository riskConfigRepository,
                                 RiskAlertRepository riskAlertRepository,
                                 AccountRepository accountRepository,
                                 TradeRepository tradeRepository,
                                 PortfolioRepository portfolioRepository,
                                 OperatorAuditService operatorAuditService) {
        this.riskConfigRepository = riskConfigRepository;
        this.riskAlertRepository = riskAlertRepository;
        this.accountRepository = accountRepository;
        this.tradeRepository = tradeRepository;
        this.portfolioRepository = portfolioRepository;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional(readOnly = true)
    public RiskConfigResponse getConfig() {
        RiskConfig config = ensureConfig();
        return toConfigResponse(config);
    }

    @Transactional
    public RiskConfigResponse updateConfig(UpdateRiskConfigRequest request) {
        RiskConfig config = ensureConfig();

        config.setMaxRiskPerTrade(request.maxRiskPerTrade());
        config.setMaxDailyLossLimit(request.maxDailyLossLimit());
        config.setMaxDrawdownLimit(request.maxDrawdownLimit());
        config.setMaxOpenPositions(request.maxOpenPositions());
        config.setCorrelationLimit(request.correlationLimit());

        RiskConfig saved = riskConfigRepository.save(config);
        operatorAuditService.recordSuccess(
            "RISK_CONFIG_UPDATED",
            "test",
            "RISK_CONFIG",
            String.valueOf(saved.getId()),
            "maxRiskPerTrade=" + saved.getMaxRiskPerTrade()
                + ", maxDailyLossLimit=" + saved.getMaxDailyLossLimit()
                + ", maxDrawdownLimit=" + saved.getMaxDrawdownLimit()
        );
        return toConfigResponse(saved);
    }

    @Transactional
    public RiskStatusResponse getStatus() {
        RiskConfig config = ensureConfig();
        Account account = accountRepository.findTopByOrderByCreatedAtDesc().orElse(null);

        BigDecimal currentDrawdown = account == null ? BigDecimal.ZERO : account.getCurrentDrawdown();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal realizedPnLToday = account == null ? BigDecimal.ZERO : tradeRepository
            .findByAccountIdAndEntryTimeAfter(account.getId(), startOfDay)
            .stream()
            .map(Trade::getPnl)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dailyLossPercent = BigDecimal.ZERO;
        if (account != null && realizedPnLToday.compareTo(BigDecimal.ZERO) < 0 && account.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
            dailyLossPercent = realizedPnLToday.abs()
                .divide(account.getInitialBalance(), 6, RoundingMode.HALF_UP);
        }

        BigDecimal openExposure = BigDecimal.ZERO;
        if (account != null) {
            List<Portfolio> positions = portfolioRepository.findByAccountId(account.getId());
            BigDecimal openValue = positions.stream()
                .map(Portfolio::getPositionValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal equity = account.getCurrentBalance().add(openValue);
            if (equity.compareTo(BigDecimal.ZERO) > 0) {
                openExposure = openValue.divide(equity, 6, RoundingMode.HALF_UP);
            }
        }

        BigDecimal positionCorrelation = new BigDecimal("0.35");

        boolean shouldTrigger = currentDrawdown.compareTo(config.getMaxDrawdownLimit().multiply(BigDecimal.valueOf(100))) >= 0
            || dailyLossPercent.compareTo(config.getMaxDailyLossLimit()) >= 0;

        if (shouldTrigger && !config.getCircuitBreakerActive()) {
            config.setCircuitBreakerActive(true);
            config.setCircuitBreakerReason("Drawdown or daily loss limit reached");
            config.setCircuitBreakerTriggeredAt(LocalDateTime.now());
            riskConfigRepository.save(config);
            riskAlertRepository.save(new RiskAlert("CIRCUIT_BREAKER", "HIGH", config.getCircuitBreakerReason(), "Trading halted"));
            operatorAuditService.recordSuccess(
                "CIRCUIT_BREAKER_TRIGGERED",
                "paper",
                "RISK_CONFIG",
                String.valueOf(config.getId()),
                config.getCircuitBreakerReason()
            );
        }

        return new RiskStatusResponse(
            currentDrawdown,
            config.getMaxDrawdownLimit().multiply(BigDecimal.valueOf(100)),
            dailyLossPercent.multiply(BigDecimal.valueOf(100)),
            config.getMaxDailyLossLimit().multiply(BigDecimal.valueOf(100)),
            openExposure.multiply(BigDecimal.valueOf(100)),
            positionCorrelation.multiply(BigDecimal.valueOf(100)),
            config.getCircuitBreakerActive(),
            config.getCircuitBreakerReason()
        );
    }

    @Transactional
    public RiskConfigResponse overrideCircuitBreaker(String environment, CircuitBreakerOverrideRequest request) {
        if ("live".equalsIgnoreCase(environment)) {
            operatorAuditService.recordFailure(
                "CIRCUIT_BREAKER_OVERRIDE",
                environment,
                "RISK_CONFIG",
                null,
                "Override blocked for live environment"
            );
            throw new IllegalArgumentException("Circuit breaker override is disabled for live environment");
        }

        if (!OVERRIDE_CODE.equals(request.confirmationCode())) {
            operatorAuditService.recordFailure(
                "CIRCUIT_BREAKER_OVERRIDE",
                environment,
                "RISK_CONFIG",
                null,
                "Invalid confirmation code"
            );
            throw new IllegalArgumentException("Invalid confirmation code");
        }

        RiskConfig config = ensureConfig();
        config.setCircuitBreakerActive(false);
        config.setCircuitBreakerReason("Overridden: " + request.reason());
        config.setCircuitBreakerOverriddenAt(LocalDateTime.now());
        riskConfigRepository.save(config);

        riskAlertRepository.save(new RiskAlert(
            "CIRCUIT_BREAKER_OVERRIDE",
            "MEDIUM",
            "Circuit breaker manually overridden",
            request.reason()
        ));

        operatorAuditService.recordSuccess(
            "CIRCUIT_BREAKER_OVERRIDE",
            environment,
            "RISK_CONFIG",
            String.valueOf(config.getId()),
            request.reason()
        );

        return toConfigResponse(config);
    }

    @Transactional(readOnly = true)
    public List<RiskAlertResponse> getAlerts() {
        return riskAlertRepository.findTop50ByOrderByTimestampDesc().stream()
            .map(alert -> new RiskAlertResponse(
                alert.getId(),
                alert.getType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getActionTaken(),
                alert.getTimestamp()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RiskConfigResponse> getCircuitBreakers() {
        return List.of(toConfigResponse(ensureConfig()));
    }

    private RiskConfigResponse toConfigResponse(RiskConfig config) {
        return new RiskConfigResponse(
            config.getMaxRiskPerTrade(),
            config.getMaxDailyLossLimit(),
            config.getMaxDrawdownLimit(),
            config.getMaxOpenPositions(),
            config.getCorrelationLimit(),
            config.getCircuitBreakerActive(),
            config.getCircuitBreakerReason()
        );
    }

    private RiskConfig ensureConfig() {
        return riskConfigRepository.findFirstByOrderByIdAsc().orElseGet(() ->
            riskConfigRepository.save(new RiskConfig(
                new BigDecimal("0.02"),
                new BigDecimal("0.05"),
                new BigDecimal("0.25"),
                5,
                new BigDecimal("0.75")
            ))
        );
    }
}
