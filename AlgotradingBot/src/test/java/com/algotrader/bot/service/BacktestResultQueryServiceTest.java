package com.algotrader.bot.service;

import com.algotrader.bot.controller.BacktestDetailsResponse;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestResultQueryServiceTest {

    @Test
    void getDetails_includesSqueezeSpecificStrategyMetrics() {
        BacktestResultRepository resultRepository = mock(BacktestResultRepository.class);
        BacktestDatasetRepository datasetRepository = mock(BacktestDatasetRepository.class);
        BacktestTelemetryService telemetryService = mock(BacktestTelemetryService.class);
        BackendOperationMetrics backendOperationMetrics = mock(BackendOperationMetrics.class);
        BacktestResultQueryService service = new BacktestResultQueryService(
            resultRepository,
            datasetRepository,
            telemetryService,
            backendOperationMetrics
        );

        BacktestResult result = new BacktestResult();
        result.setId(42L);
        result.setStrategyId("SQUEEZE_BREAKOUT_REGIME_CONFIRMATION");
        result.setDatasetId(null);
        result.setDatasetName("Squeeze Test");
        result.setExperimentName("Squeeze Reporting");
        result.setExperimentKey("squeeze-reporting");
        result.setSymbol("SPY");
        result.setTimeframe("1h");
        result.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        result.setValidationStatus(BacktestResult.ValidationStatus.PASSED);
        result.setInitialBalance(new BigDecimal("1000"));
        result.setFinalBalance(new BigDecimal("1080"));
        result.setSharpeRatio(new BigDecimal("1.2"));
        result.setProfitFactor(new BigDecimal("1.4"));
        result.setWinRate(new BigDecimal("50"));
        result.setMaxDrawdown(new BigDecimal("8"));
        result.setTotalTrades(2);
        result.setFeesBps(10);
        result.setSlippageBps(3);
        result.setStartDate(LocalDateTime.parse("2025-01-01T00:00:00"));
        result.setEndDate(LocalDateTime.parse("2025-02-01T00:00:00"));
        result.setTimestamp(LocalDateTime.parse("2025-02-02T00:00:00"));
        result.setExecutionStage(BacktestResult.ExecutionStage.COMPLETED);
        result.setProgressPercent(100);
        result.setProcessedCandles(1000);
        result.setTotalCandles(1000);
        result.setStatusMessage("done");

        result.addTradeSeriesItem(trade(
            LocalDateTime.parse("2025-01-10T09:00:00"),
            LocalDateTime.parse("2025-01-10T15:00:00"),
            new BigDecimal("2.50")
        ));
        result.addTradeSeriesItem(trade(
            LocalDateTime.parse("2025-01-11T09:00:00"),
            LocalDateTime.parse("2025-01-11T12:00:00"),
            new BigDecimal("-1.00")
        ));

        when(resultRepository.findById(42L)).thenReturn(Optional.of(result));

        BacktestDetailsResponse details = service.getDetails(42L);

        assertFalse(details.strategyMetrics().isEmpty());
        assertEquals("breakout_failure_rate", details.strategyMetrics().get(0).key());
        assertEquals("50.00%", details.strategyMetrics().get(0).displayValue());
        assertEquals("average_hold_hours", details.strategyMetrics().get(1).key());
        assertEquals("4.50h", details.strategyMetrics().get(1).displayValue());
    }

    private BacktestTradeSeriesItem trade(LocalDateTime entryTime,
                                          LocalDateTime exitTime,
                                          BigDecimal returnPct) {
        BacktestTradeSeriesItem item = new BacktestTradeSeriesItem();
        item.setSymbol("SPY");
        item.setPositionSide(PositionSide.LONG);
        item.setEntryTime(entryTime);
        item.setExitTime(exitTime);
        item.setEntryPrice(new BigDecimal("100"));
        item.setExitPrice(new BigDecimal("101"));
        item.setQuantity(BigDecimal.ONE);
        item.setEntryValue(new BigDecimal("100"));
        item.setExitValue(new BigDecimal("101"));
        item.setReturnPct(returnPct);
        return item;
    }
}
