package com.algotrader.bot.analysis;

import com.algotrader.bot.BotApplication;
import com.algotrader.bot.backtest.BacktestEquityPointSample;
import com.algotrader.bot.backtest.BacktestMetrics;
import com.algotrader.bot.backtest.BacktestSimulationEngine;
import com.algotrader.bot.backtest.BacktestSimulationRequest;
import com.algotrader.bot.backtest.BacktestSimulationResult;
import com.algotrader.bot.backtest.BacktestTradeSample;
import com.algotrader.bot.backtest.BacktestValidator;
import com.algotrader.bot.backtest.MetricsResult;
import com.algotrader.bot.backtest.MonteCarloResult;
import com.algotrader.bot.backtest.MonteCarloSimulator;
import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.backtest.ValidationReport;
import com.algotrader.bot.backtest.WalkForwardResult;
import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.service.BacktestAlgorithmType;
import com.algotrader.bot.service.marketdata.MarketDataQueryMode;
import com.algotrader.bot.service.marketdata.MarketDataQueryService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PhaseThreeStrategyAuditRunner {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int FEES_BPS = 10;
    private static final int SLIPPAGE_BPS = 3;
    private static final BigDecimal COST_RATE = BigDecimal.valueOf(FEES_BPS + SLIPPAGE_BPS)
        .divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);
    private static final long AUDIT_DATASET_ID = 12L;
    private static final String AUDIT_DATASET_CHECKSUM =
        "b93c95da97c05f4edf4d706b80d33fcfab752f4f4d6f11f003fa3aca2fe2d326";
    private static final String PRIMARY_SYMBOL = "BTC/USDT";
    private static final LocalDateTime FULL_SAMPLE_START = LocalDateTime.parse("2024-03-12T00:00:00");
    private static final LocalDateTime HOLDOUT_SPLIT = LocalDateTime.parse("2025-07-01T00:00:00");
    private static final int MONTE_CARLO_ITERATIONS = 250;
    private static final long MONTE_CARLO_SEED = 42L;
    private static final String ETF_PACK_LIMITATION =
        "The frozen dataset pack still lacks an approved intraday ETF anchor, so the daily ETF pack is recorded as a "
            + "coverage limit rather than misused for `15m`/`1h` strategy claims.";
    private static final List<AuditStrategySpec> COMPARATOR_STRATEGIES = List.of(
        new AuditStrategySpec(
            BacktestAlgorithmType.BUY_AND_HOLD,
            "1d",
            PRIMARY_SYMBOL,
            null,
            "Passive BTC benchmark on the same audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.SMA_CROSSOVER,
            "4h",
            PRIMARY_SYMBOL,
            null,
            "Current paper-monitor candidate for single-symbol BTC review."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT,
            "1d",
            PRIMARY_SYMBOL,
            null,
            "Strongest current single-symbol breakout path on the same audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION,
            "1d",
            "DATASET_UNIVERSE",
            null,
            "Strongest current dataset-universe rotation path on the same audit anchor."
        )
    );
    private static final List<AuditStrategySpec> PHASE_THREE_STRATEGIES = List.of(
        new AuditStrategySpec(
            BacktestAlgorithmType.OPENING_RANGE_VWAP_BREAKOUT,
            "15m",
            PRIMARY_SYMBOL,
            BacktestAlgorithmType.SMA_CROSSOVER,
            "Compare against the current paper-monitor candidate on the same BTC audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.VWAP_PULLBACK_CONTINUATION,
            "15m",
            PRIMARY_SYMBOL,
            BacktestAlgorithmType.SMA_CROSSOVER,
            "Compare against the current paper-monitor candidate on the same BTC audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.EXHAUSTION_REVERSAL_FADE,
            "15m",
            PRIMARY_SYMBOL,
            BacktestAlgorithmType.SMA_CROSSOVER,
            "Compare against the current paper-monitor candidate on the same BTC audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.MULTI_TIMEFRAME_EMA_ADX_PULLBACK,
            "1h",
            PRIMARY_SYMBOL,
            BacktestAlgorithmType.SMA_CROSSOVER,
            "Compare against the current paper-monitor candidate on the same BTC audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.SQUEEZE_BREAKOUT_REGIME_CONFIRMATION,
            "1h",
            PRIMARY_SYMBOL,
            BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT,
            "Compare against the strongest current BTC breakout path on the same audit anchor."
        ),
        new AuditStrategySpec(
            BacktestAlgorithmType.RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER,
            "1h",
            "DATASET_UNIVERSE",
            BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION,
            "Compare against the strongest current dataset-universe rotation path on the same audit anchor."
        )
    );

    private PhaseThreeStrategyAuditRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path output = resolveOutputPath();
        Files.createDirectories(output.getParent());
        System.setProperty("jwt.secret", "phase-three-strategy-audit-local-secret-2026-03-27-123456");

        ConfigurableApplicationContext context = new SpringApplicationBuilder(BotApplication.class)
            .web(WebApplicationType.NONE)
            .properties(Map.of(
                "spring.main.banner-mode", "off",
                "server.port", "0",
                "logging.level.root", "WARN",
                "logging.level.com.algotrader.bot", "INFO"
            ))
            .run(args);

        try {
            AuditReport report = generateReport(context);
            String markdown = renderReport(report);
            Files.writeString(output, markdown, StandardCharsets.UTF_8);
            System.out.println(markdown);
            System.out.println("Phase 3 strategy audit report written to " + output.toAbsolutePath());
        } finally {
            context.close();
        }
    }

    private static AuditReport generateReport(ConfigurableApplicationContext context) {
        BacktestDatasetRepository datasetRepository = context.getBean(BacktestDatasetRepository.class);
        MarketDataQueryService marketDataQueryService = context.getBean(MarketDataQueryService.class);
        BacktestSimulationEngine simulationEngine = context.getBean(BacktestSimulationEngine.class);
        BacktestStrategyRegistry strategyRegistry = context.getBean(BacktestStrategyRegistry.class);
        BacktestMetrics backtestMetrics = context.getBean(BacktestMetrics.class);
        MonteCarloSimulator monteCarloSimulator = new MonteCarloSimulator();
        BacktestValidator backtestValidator = new BacktestValidator();

        BacktestDataset dataset = datasetRepository.findById(AUDIT_DATASET_ID)
            .orElseThrow(() -> new IllegalStateException("Missing audit dataset " + AUDIT_DATASET_ID));
        if (!AUDIT_DATASET_CHECKSUM.equals(dataset.getChecksumSha256())) {
            throw new IllegalStateException(
                "Dataset " + AUDIT_DATASET_ID + " checksum mismatch. Expected " + AUDIT_DATASET_CHECKSUM
                    + " but found " + dataset.getChecksumSha256()
            );
        }

        EnumMap<BacktestAlgorithmType, StrategyAuditRecord> records = new EnumMap<>(BacktestAlgorithmType.class);
        List<AuditStrategySpec> strategies = new ArrayList<>();
        strategies.addAll(COMPARATOR_STRATEGIES);
        strategies.addAll(PHASE_THREE_STRATEGIES);

        for (AuditStrategySpec spec : strategies) {
            SimulationWindow fullSampleWindow = new SimulationWindow("FULL_SAMPLE", FULL_SAMPLE_START, dataset.getDataEnd());
            SimulationWindow inSampleWindow = new SimulationWindow("IN_SAMPLE", FULL_SAMPLE_START, HOLDOUT_SPLIT.minusSeconds(1));
            SimulationWindow outOfSampleWindow = new SimulationWindow("OUT_OF_SAMPLE", HOLDOUT_SPLIT, dataset.getDataEnd());

            Scorecard fullSample = runWindow(spec, fullSampleWindow, marketDataQueryService, simulationEngine,
                backtestMetrics, monteCarloSimulator, backtestValidator, strategyRegistry, dataset);
            Scorecard inSample = runWindow(spec, inSampleWindow, marketDataQueryService, simulationEngine,
                backtestMetrics, monteCarloSimulator, backtestValidator, strategyRegistry, dataset);
            Scorecard outOfSample = runWindow(spec, outOfSampleWindow, marketDataQueryService, simulationEngine,
                backtestMetrics, monteCarloSimulator, backtestValidator, strategyRegistry, dataset);

            WalkForwardResult walkForward = new WalkForwardResult(
                toBacktestResult(spec, inSample, dataset),
                toBacktestResult(spec, outOfSample, dataset)
            );
            records.put(spec.algorithmType(), new StrategyAuditRecord(spec, fullSample, inSample, outOfSample, walkForward));
        }

        return new AuditReport(dataset, records);
    }

    private static Scorecard runWindow(AuditStrategySpec spec,
                                       SimulationWindow window,
                                       MarketDataQueryService marketDataQueryService,
                                       BacktestSimulationEngine simulationEngine,
                                       BacktestMetrics backtestMetrics,
                                       MonteCarloSimulator monteCarloSimulator,
                                       BacktestValidator backtestValidator,
                                       BacktestStrategyRegistry strategyRegistry,
                                       BacktestDataset dataset) {
        Set<String> requestedSymbols = strategyRegistry.getStrategy(spec.algorithmType()).getSelectionMode().name().equals("SINGLE_SYMBOL")
            ? Set.of(PRIMARY_SYMBOL)
            : Set.of();
        List<OHLCVData> candles = marketDataQueryService.queryCandlesForDataset(
                dataset.getId(),
                spec.timeframe(),
                window.start(),
                window.end(),
                requestedSymbols,
                MarketDataQueryMode.BEST_AVAILABLE
            ).candles().stream()
            .map(candle -> candle.toOhlcvData())
            .sorted(Comparator.comparing(OHLCVData::getTimestamp).thenComparing(OHLCVData::getSymbol))
            .toList();

        if (candles.isEmpty()) {
            throw new IllegalStateException("No candles returned for " + spec.algorithmType() + " in " + window.label());
        }

        BacktestSimulationResult simulationResult = simulationEngine.simulate(
            spec.algorithmType(),
            new BacktestSimulationRequest(
                candles,
                PRIMARY_SYMBOL,
                spec.timeframe(),
                INITIAL_BALANCE,
                FEES_BPS,
                SLIPPAGE_BPS
            )
        );

        List<Trade> trades = toTrades(simulationResult.tradeSeries());
        List<BigDecimal> equityCurve = simulationResult.equitySeries().stream()
            .map(BacktestEquityPointSample::equity)
            .toList();
        LocalDateTime effectiveEnd = candles.getLast().getTimestamp();
        MetricsResult metrics = backtestMetrics.calculateMetrics(
            trades,
            equityCurve,
            INITIAL_BALANCE,
            candles.getFirst().getTimestamp(),
            effectiveEnd
        );
        MonteCarloResult monteCarloResult = monteCarloSimulator.simulate(
            trades,
            INITIAL_BALANCE,
            MONTE_CARLO_ITERATIONS,
            MONTE_CARLO_SEED
        );
        ValidationReport validationReport = backtestValidator.validate(metrics, monteCarloResult);

        BigDecimal feeDragEstimate = estimateFeeDrag(simulationResult.tradeSeries());
        BigDecimal exposurePct = calculateExposurePct(simulationResult.tradeSeries(), candles.getFirst().getTimestamp(), effectiveEnd);
        BigDecimal averageHoldHours = calculateAverageHoldHours(simulationResult.tradeSeries());
        BigDecimal netReturnPct = simulationResult.finalBalance()
            .subtract(INITIAL_BALANCE)
            .divide(INITIAL_BALANCE, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);

        String note = describeEvidence(simulationResult.totalTrades(), netReturnPct, validationReport);
        return new Scorecard(
            window,
            simulationResult.finalBalance().setScale(2, RoundingMode.HALF_UP),
            netReturnPct,
            metrics.getSharpeRatio().setScale(4, RoundingMode.HALF_UP),
            metrics.getProfitFactor().setScale(4, RoundingMode.HALF_UP),
            metrics.getWinRate().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
            metrics.getMaxDrawdown().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
            metrics.getTotalTrades(),
            exposurePct,
            averageHoldHours,
            feeDragEstimate,
            validationReport.getOverallStatus().name(),
            note
        );
    }

    private static List<Trade> toTrades(List<BacktestTradeSample> tradeSamples) {
        List<Trade> trades = new ArrayList<>(tradeSamples.size());
        for (BacktestTradeSample sample : tradeSamples) {
            Trade trade = new Trade();
            trade.setAccountId(0L);
            trade.setSymbol(sample.symbol());
            trade.setSignalType(sample.side() == PositionSide.SHORT ? Trade.SignalType.SHORT : Trade.SignalType.BUY);
            trade.setPositionSide(sample.side());
            trade.setEntryTime(sample.entryTime());
            trade.setExitTime(sample.exitTime());
            trade.setEntryPrice(sample.entryPrice());
            trade.setExitPrice(sample.exitPrice());
            trade.setPositionSize(sample.quantity());
            trade.setRiskAmount(BigDecimal.ONE);
            trade.setStopLoss(BigDecimal.ONE);
            trade.setTakeProfit(BigDecimal.ONE);
            trade.setPnl(sample.exitValue().subtract(sample.entryValue()).setScale(8, RoundingMode.HALF_UP));
            trade.setActualFees(sample.entryValue().multiply(COST_RATE).setScale(8, RoundingMode.HALF_UP));
            trade.setActualSlippage(sample.exitValue().multiply(COST_RATE).setScale(8, RoundingMode.HALF_UP));
            trades.add(trade);
        }
        return trades;
    }

    private static BigDecimal estimateFeeDrag(List<BacktestTradeSample> tradeSamples) {
        BigDecimal total = BigDecimal.ZERO;
        for (BacktestTradeSample sample : tradeSamples) {
            BigDecimal entryDrag = sample.entryValue().multiply(COST_RATE);
            BigDecimal exitDrag = sample.exitValue().multiply(COST_RATE);
            total = total.add(entryDrag).add(exitDrag);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateExposurePct(List<BacktestTradeSample> tradeSamples,
                                                   LocalDateTime start,
                                                   LocalDateTime end) {
        long totalSeconds = Math.max(1L, Duration.between(start, end).getSeconds());
        long exposureSeconds = 0L;
        for (BacktestTradeSample sample : tradeSamples) {
            exposureSeconds += Math.max(0L, Duration.between(sample.entryTime(), sample.exitTime()).getSeconds());
        }
        return BigDecimal.valueOf(exposureSeconds)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalSeconds), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateAverageHoldHours(List<BacktestTradeSample> tradeSamples) {
        if (tradeSamples.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalHours = BigDecimal.ZERO;
        for (BacktestTradeSample sample : tradeSamples) {
            long seconds = Math.max(0L, Duration.between(sample.entryTime(), sample.exitTime()).getSeconds());
            totalHours = totalHours.add(BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP));
        }
        return totalHours.divide(BigDecimal.valueOf(tradeSamples.size()), 2, RoundingMode.HALF_UP);
    }

    private static String describeEvidence(int totalTrades,
                                           BigDecimal netReturnPct,
                                           ValidationReport validationReport) {
        if (totalTrades == 0) {
            return "No trades in window; evidence is sparse.";
        }
        if (totalTrades < 30) {
            return "Trade count stays below the protocol comfort threshold; keep interpretation conservative.";
        }
        if (netReturnPct.compareTo(BigDecimal.ZERO) <= 0) {
            return "Net return is negative after costs; this window weakens the strategy case.";
        }
        if (validationReport.getOverallStatus() != BacktestResult.ValidationStatus.PRODUCTION_READY) {
            return "Mechanically valid run, but the current validator still rejects it.";
        }
        return "Passed the current validator in this window, but that still does not imply live-readiness.";
    }

    private static BacktestResult toBacktestResult(AuditStrategySpec spec, Scorecard scorecard, BacktestDataset dataset) {
        BacktestResult result = new BacktestResult();
        result.setStrategyId(spec.algorithmType().name());
        result.setDatasetId(dataset.getId());
        result.setDatasetName(dataset.getName());
        result.setSymbol(spec.displaySymbol());
        result.setTimeframe(spec.timeframe());
        result.setInitialBalance(INITIAL_BALANCE);
        result.setFinalBalance(scorecard.finalBalance());
        result.setSharpeRatio(scorecard.sharpeRatio());
        result.setProfitFactor(scorecard.profitFactor());
        result.setWinRate(scorecard.winRatePercent());
        result.setMaxDrawdown(scorecard.maxDrawdownPercent());
        result.setTotalTrades(scorecard.totalTrades());
        result.setValidationStatus(BacktestResult.ValidationStatus.valueOf(scorecard.validationStatus()));
        result.setStartDate(scorecard.window().start());
        result.setEndDate(scorecard.window().end());
        return result;
    }

    private static String renderReport(AuditReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Phase 3 Strategy Audit Report\n\n");
        builder.append("Generated by `phaseThreeStrategyAudit` against dataset `#")
            .append(report.dataset().getId())
            .append("` (`")
            .append(report.dataset().getName())
            .append("`) on the local normalized market-data store.\n\n");
        builder.append("Frozen audit inputs:\n");
        builder.append("- Dataset checksum: `").append(report.dataset().getChecksumSha256()).append("`\n");
        builder.append("- Fees: `").append(FEES_BPS).append("` bps\n");
        builder.append("- Slippage: `").append(SLIPPAGE_BPS).append("` bps\n");
        builder.append("- Fill model: next-bar-open\n");
        builder.append("- Holdout split: `")
            .append(FULL_SAMPLE_START.toLocalDate())
            .append("` to `")
            .append(HOLDOUT_SPLIT.toLocalDate())
            .append("` in-sample, `")
            .append(HOLDOUT_SPLIT.toLocalDate())
            .append("` to `")
            .append(report.dataset().getDataEnd().toLocalDate())
            .append("` out-of-sample\n");
        builder.append("- Frozen pack limitation: ").append(ETF_PACK_LIMITATION).append("\n\n");

        builder.append("## Comparator Snapshot\n\n");
        builder.append("| Strategy | Timeframe | Scope | Full-Sample Return % | OOS Return % | OOS Trades | Validator |\n");
        builder.append("| --- | --- | --- | ---: | ---: | ---: | --- |\n");
        for (AuditStrategySpec spec : COMPARATOR_STRATEGIES) {
            StrategyAuditRecord record = report.records().get(spec.algorithmType());
            builder.append("| ").append(spec.algorithmType().name())
                .append(" | ").append(spec.timeframe())
                .append(" | ").append(spec.displaySymbol())
                .append(" | ").append(format(record.fullSample().netReturnPct()))
                .append(" | ").append(format(record.outOfSample().netReturnPct()))
                .append(" | ").append(record.outOfSample().totalTrades())
                .append(" | ").append(record.outOfSample().validationStatus())
                .append(" |\n");
        }

        builder.append("\n## Phase 3 Full-Sample Scorecards\n\n");
        builder.append("| Strategy | Timeframe | Scope | Return % | Sharpe | Profit Factor | Win % | Max DD % | Trades | Exposure % | Avg Hold Hrs | Fee Drag | Validator |\n");
        builder.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (AuditStrategySpec spec : PHASE_THREE_STRATEGIES) {
            Scorecard scorecard = report.records().get(spec.algorithmType()).fullSample();
            builder.append("| ").append(spec.algorithmType().name())
                .append(" | ").append(spec.timeframe())
                .append(" | ").append(spec.displaySymbol())
                .append(" | ").append(format(scorecard.netReturnPct()))
                .append(" | ").append(format(scorecard.sharpeRatio()))
                .append(" | ").append(format(scorecard.profitFactor()))
                .append(" | ").append(format(scorecard.winRatePercent()))
                .append(" | ").append(format(scorecard.maxDrawdownPercent()))
                .append(" | ").append(scorecard.totalTrades())
                .append(" | ").append(format(scorecard.exposurePct()))
                .append(" | ").append(format(scorecard.averageHoldHours()))
                .append(" | ").append(scorecard.feeDragEstimate())
                .append(" | ").append(scorecard.validationStatus())
                .append(" |\n");
        }

        builder.append("\n## Holdout And Benchmark Comparison\n\n");
        builder.append("| Strategy | Comparator | In-Sample Return % | OOS Return % | OOS Trades | Walk-Forward Ratio % | Vs Buy/Hold OOS % | Vs Comparator OOS % |\n");
        builder.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        StrategyAuditRecord benchmark = report.records().get(BacktestAlgorithmType.BUY_AND_HOLD);
        for (AuditStrategySpec spec : PHASE_THREE_STRATEGIES) {
            StrategyAuditRecord record = report.records().get(spec.algorithmType());
            StrategyAuditRecord comparator = report.records().get(spec.comparatorAlgorithmType());
            builder.append("| ").append(spec.algorithmType().name())
                .append(" | ").append(spec.comparatorAlgorithmType().name())
                .append(" | ").append(format(record.inSample().netReturnPct()))
                .append(" | ").append(format(record.outOfSample().netReturnPct()))
                .append(" | ").append(record.outOfSample().totalTrades())
                .append(" | ").append(format(evaluateWalkForwardRatioPercent(record)))
                .append(" | ").append(format(delta(record.outOfSample().netReturnPct(), benchmark.outOfSample().netReturnPct())))
                .append(" | ").append(format(delta(record.outOfSample().netReturnPct(), comparator.outOfSample().netReturnPct())))
                .append(" |\n");
        }

        builder.append("\n## Evidence Sheets\n\n");
        for (AuditStrategySpec spec : PHASE_THREE_STRATEGIES) {
            StrategyAuditRecord record = report.records().get(spec.algorithmType());
            StrategyAuditRecord comparator = report.records().get(spec.comparatorAlgorithmType());
            DispositionSummary disposition = deriveDisposition(record, benchmark, comparator);

            builder.append("### ").append(spec.algorithmType().name()).append("\n\n");
            builder.append("- Requested timeframe: `").append(spec.timeframe()).append("`\n");
            builder.append("- Scope: `").append(spec.displaySymbol()).append("`\n");
            builder.append("- Comparator: `").append(spec.comparatorAlgorithmType().name()).append("`")
                .append(" - ").append(spec.comparatorNote()).append("\n");
            builder.append("- Full sample: `").append(format(record.fullSample().netReturnPct())).append("%` return, `")
                .append(record.fullSample().totalTrades()).append("` trades, `")
                .append(format(record.fullSample().maxDrawdownPercent())).append("%` max drawdown\n");
            builder.append("- Out of sample: `").append(format(record.outOfSample().netReturnPct())).append("%` return, `")
                .append(record.outOfSample().totalTrades()).append("` trades, walk-forward ratio `")
                .append(format(evaluateWalkForwardRatioPercent(record))).append("%`\n");
            builder.append("- Delta versus `BUY_AND_HOLD` OOS: `")
                .append(format(delta(record.outOfSample().netReturnPct(), benchmark.outOfSample().netReturnPct())))
                .append("%`\n");
            builder.append("- Delta versus `").append(spec.comparatorAlgorithmType().name()).append("` OOS: `")
                .append(format(delta(record.outOfSample().netReturnPct(), comparator.outOfSample().netReturnPct())))
                .append("%`\n");
            builder.append("- Evidence note: ").append(record.outOfSample().note()).append("\n");
            builder.append("- Disposition: `").append(disposition.label()).append("` - ")
                .append(disposition.reason()).append("\n");
            builder.append("- Catalog action: ").append(disposition.catalogAction()).append("\n\n");
        }

        builder.append("## Dispositions And Next Actions\n\n");
        builder.append("| Strategy | Disposition | Catalog Action |\n");
        builder.append("| --- | --- | --- |\n");
        for (AuditStrategySpec spec : PHASE_THREE_STRATEGIES) {
            StrategyAuditRecord record = report.records().get(spec.algorithmType());
            StrategyAuditRecord comparator = report.records().get(spec.comparatorAlgorithmType());
            DispositionSummary disposition = deriveDisposition(record, benchmark, comparator);
            builder.append("| `").append(spec.algorithmType().name()).append("` | `")
                .append(disposition.label()).append("` | ")
                .append(disposition.catalogAction()).append(" |\n");
        }

        builder.append("\n## Audit Notes\n\n");
        builder.append("- This report keeps the frozen `10` bps fee plus `3` bps slippage baseline from `docs/STRATEGY_AUDIT_PROTOCOL.md`.\n");
        builder.append("- The checked-in ETF audit pack remains part of the protocol record, but it cannot honestly clear `15m` or `1h` hypotheses while it only provides `1d` bars.\n");
        builder.append("- Because that intraday ETF coverage gap remains unresolved, no Phase 3 strategy can move beyond `research-only` in this audit round even when the BTC anchor result is directionally encouraging.\n");
        builder.append("- The reproducible generated artifact lives at `AlgotradingBot/build/reports/phase-three-strategy-audit/report.md` after each `./gradlew phaseThreeStrategyAudit` or `./gradlew.bat phaseThreeStrategyAudit` run.\n");
        return builder.toString();
    }

    private static DispositionSummary deriveDisposition(StrategyAuditRecord record,
                                                        StrategyAuditRecord benchmark,
                                                        StrategyAuditRecord comparator) {
        boolean fullSampleNegative = record.fullSample().netReturnPct().compareTo(BigDecimal.ZERO) <= 0;
        boolean outOfSampleNegative = record.outOfSample().netReturnPct().compareTo(BigDecimal.ZERO) <= 0;
        boolean trailsBenchmark = record.outOfSample().netReturnPct().compareTo(benchmark.outOfSample().netReturnPct()) <= 0;
        boolean trailsComparator = record.outOfSample().netReturnPct().compareTo(comparator.outOfSample().netReturnPct()) <= 0;

        if (fullSampleNegative && outOfSampleNegative && trailsBenchmark && trailsComparator) {
            return new DispositionSummary(
                "reject",
                "Both the full sample and holdout stayed negative after costs while trailing the passive benchmark and the selected legacy comparator.",
                "Keep the implementation visible for historical comparison only and require a material redesign before any new paper-follow-up."
            );
        }
        return new DispositionSummary(
            "research-only",
            "The run stays mechanically valid, but the frozen pack still lacks an approved intraday ETF anchor and the current evidence is not strong enough to promote beyond research.",
            "Keep the strategy in the research catalog, rerun it after an approved intraday ETF pack exists, and do not move it into shadow paper monitoring yet."
        );
    }

    private static BigDecimal evaluateWalkForwardRatioPercent(StrategyAuditRecord record) {
        if (record.inSample().sharpeRatio().compareTo(BigDecimal.ZERO) <= 0
            || record.outOfSample().sharpeRatio().compareTo(BigDecimal.ZERO) <= 0
            || record.outOfSample().totalTrades() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return record.walkForward().getPerformanceRatio()
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal delta(BigDecimal left, BigDecimal right) {
        return left.subtract(right).setScale(2, RoundingMode.HALF_UP);
    }

    private static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Path resolveOutputPath() {
        String configured = System.getProperty("phaseThreeStrategyAudit.output");
        if (configured == null || configured.isBlank()) {
            return Path.of("build", "reports", "phase-three-strategy-audit", "report.md");
        }
        return Path.of(configured);
    }

    private record AuditStrategySpec(
        BacktestAlgorithmType algorithmType,
        String timeframe,
        String displaySymbol,
        BacktestAlgorithmType comparatorAlgorithmType,
        String comparatorNote
    ) {
    }

    private record SimulationWindow(String label, LocalDateTime start, LocalDateTime end) {
    }

    private record Scorecard(
        SimulationWindow window,
        BigDecimal finalBalance,
        BigDecimal netReturnPct,
        BigDecimal sharpeRatio,
        BigDecimal profitFactor,
        BigDecimal winRatePercent,
        BigDecimal maxDrawdownPercent,
        int totalTrades,
        BigDecimal exposurePct,
        BigDecimal averageHoldHours,
        BigDecimal feeDragEstimate,
        String validationStatus,
        String note
    ) {
    }

    private record StrategyAuditRecord(
        AuditStrategySpec spec,
        Scorecard fullSample,
        Scorecard inSample,
        Scorecard outOfSample,
        WalkForwardResult walkForward
    ) {
    }

    private record AuditReport(
        BacktestDataset dataset,
        Map<BacktestAlgorithmType, StrategyAuditRecord> records
    ) {
    }

    private record DispositionSummary(String label, String reason, String catalogAction) {
    }
}
