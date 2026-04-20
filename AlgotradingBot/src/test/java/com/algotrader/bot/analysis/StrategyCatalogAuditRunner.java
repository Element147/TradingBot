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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StrategyCatalogAuditRunner {

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
    private static final LocalDateTime HOLDOUT_END = LocalDateTime.parse("2026-03-13T00:00:00");
    private static final int MONTE_CARLO_ITERATIONS = 250;
    private static final long MONTE_CARLO_SEED = 42L;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final List<AuditStrategySpec> STRATEGIES = List.of(
        new AuditStrategySpec(BacktestAlgorithmType.BUY_AND_HOLD, "1d", PRIMARY_SYMBOL),
        new AuditStrategySpec(BacktestAlgorithmType.SMA_CROSSOVER, "4h", PRIMARY_SYMBOL),
        new AuditStrategySpec(BacktestAlgorithmType.BOLLINGER_BANDS, "4h", PRIMARY_SYMBOL),
        new AuditStrategySpec(BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT, "1d", PRIMARY_SYMBOL),
        new AuditStrategySpec(BacktestAlgorithmType.TREND_PULLBACK_CONTINUATION, "4h", PRIMARY_SYMBOL),
        new AuditStrategySpec(BacktestAlgorithmType.REGIME_FILTERED_MEAN_REVERSION, "4h", PRIMARY_SYMBOL),
        new AuditStrategySpec(BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION, "1d", "DATASET_UNIVERSE"),
        new AuditStrategySpec(BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE, "1d", "DATASET_UNIVERSE"),
        new AuditStrategySpec(BacktestAlgorithmType.ICHIMOKU_TREND, "1d", PRIMARY_SYMBOL)
    );

    private StrategyCatalogAuditRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path output = resolveOutputPath();
        Files.createDirectories(output.getParent());
        System.setProperty("jwt.secret", "strategy-catalog-audit-local-secret-2026-03-27-123456");

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
            System.out.println("Strategy catalog audit report written to " + output.toAbsolutePath());
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
        for (AuditStrategySpec spec : STRATEGIES) {
            SimulationWindow fullSampleWindow = new SimulationWindow("FULL_SAMPLE", FULL_SAMPLE_START, dataset.getDataEnd());
            SimulationWindow inSampleWindow = new SimulationWindow("IN_SAMPLE", FULL_SAMPLE_START, HOLDOUT_SPLIT.minusSeconds(1));
            SimulationWindow outOfSampleWindow = new SimulationWindow("OUT_OF_SAMPLE", HOLDOUT_SPLIT, dataset.getDataEnd());

            Scorecard fullSample = runWindow(spec, fullSampleWindow, marketDataQueryService, simulationEngine, backtestMetrics,
                monteCarloSimulator, backtestValidator, strategyRegistry, dataset);
            Scorecard inSample = runWindow(spec, inSampleWindow, marketDataQueryService, simulationEngine, backtestMetrics,
                monteCarloSimulator, backtestValidator, strategyRegistry, dataset);
            Scorecard outOfSample = runWindow(spec, outOfSampleWindow, marketDataQueryService, simulationEngine, backtestMetrics,
                monteCarloSimulator, backtestValidator, strategyRegistry, dataset);

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
        MonteCarloResult monteCarloResult = monteCarloSimulator.simulate(trades, INITIAL_BALANCE, MONTE_CARLO_ITERATIONS, MONTE_CARLO_SEED);
        ValidationReport validationReport = backtestValidator.validate(metrics, monteCarloResult);

        BigDecimal feeDragEstimate = estimateFeeDrag(simulationResult.tradeSeries());
        BigDecimal exposurePct = calculateExposurePct(simulationResult.tradeSeries(), candles.getFirst().getTimestamp(), effectiveEnd);
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
        builder.append("# Strategy Catalog Audit Report\n\n");
        builder.append("Generated by `strategyCatalogAudit` against dataset `#")
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
            .append("` out-of-sample\n\n");

        builder.append("## Full Sample Scorecards\n\n");
        builder.append("| Strategy | Timeframe | Scope | Return % | Sharpe | Profit Factor | Win % | Max DD % | Trades | Exposure % | Fee Drag | Validator |\n");
        builder.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (StrategyAuditRecord record : report.records().values()) {
            Scorecard scorecard = record.fullSample();
            builder.append("| ").append(record.spec().algorithmType().name())
                .append(" | ").append(record.spec().timeframe())
                .append(" | ").append(record.spec().displaySymbol())
                .append(" | ").append(format(scorecard.netReturnPct()))
                .append(" | ").append(format(scorecard.sharpeRatio()))
                .append(" | ").append(format(scorecard.profitFactor()))
                .append(" | ").append(format(scorecard.winRatePercent()))
                .append(" | ").append(format(scorecard.maxDrawdownPercent()))
                .append(" | ").append(scorecard.totalTrades())
                .append(" | ").append(format(scorecard.exposurePct()))
                .append(" | ").append(scorecard.feeDragEstimate())
                .append(" | ").append(scorecard.validationStatus())
                .append(" |\n");
        }

        StrategyAuditRecord benchmark = report.records().get(BacktestAlgorithmType.BUY_AND_HOLD);
        builder.append("\n## Holdout And Walk-Forward Summary\n\n");
        builder.append("| Strategy | In-Sample Return % | Out-of-Sample Return % | OOS Trades | OOS Validator | Walk-Forward Ratio % | Walk-Forward Pass | Vs Buy/Hold OOS % |\n");
        builder.append("| --- | ---: | ---: | ---: | --- | ---: | --- | ---: |\n");
        for (StrategyAuditRecord record : report.records().values()) {
            BigDecimal benchmarkDelta = record.outOfSample().netReturnPct().subtract(benchmark.outOfSample().netReturnPct())
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal walkForwardRatioPct = evaluateWalkForwardRatioPercent(record);
            boolean walkForwardPass = evaluateWalkForwardPass(record);
            builder.append("| ").append(record.spec().algorithmType().name())
                .append(" | ").append(format(record.inSample().netReturnPct()))
                .append(" | ").append(format(record.outOfSample().netReturnPct()))
                .append(" | ").append(record.outOfSample().totalTrades())
                .append(" | ").append(record.outOfSample().validationStatus())
                .append(" | ").append(format(walkForwardRatioPct))
                .append(" | ").append(walkForwardPass ? "PASS" : "FAIL")
                .append(" | ").append(format(benchmarkDelta))
                .append(" |\n");
        }

        builder.append("\n## Evidence Notes\n\n");
        for (StrategyAuditRecord record : report.records().values()) {
            BigDecimal walkForwardRatioPct = evaluateWalkForwardRatioPercent(record);
            builder.append("- `").append(record.spec().algorithmType().name()).append("`: ")
                .append(record.outOfSample().note())
                .append(" Full sample `").append(format(record.fullSample().netReturnPct())).append("%`, out-of-sample `")
                .append(format(record.outOfSample().netReturnPct())).append("%`, walk-forward ratio `")
                .append(format(walkForwardRatioPct)).append("%`.\n");
        }

        builder.append("\n## Comparison Notes\n\n");
        builder.append("- `BUY_AND_HOLD` remains the passive benchmark for single-symbol BTC review.\n");
        builder.append("- Dataset-universe strategies are still compared against that benchmark only as a conservative reference, not as a like-for-like portfolio benchmark.\n");
        builder.append("- `Walk-Forward Ratio %` uses the repo's current `WalkForwardResult` seam, but the report fails closed when in-sample or out-of-sample Sharpe is non-positive or when the out-of-sample window has no trades.\n");
        builder.append("- Fee drag is an executed-notional estimate under the frozen `10` bps fee plus `3` bps slippage baseline.\n");
        return builder.toString();
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

    private static boolean evaluateWalkForwardPass(StrategyAuditRecord record) {
        return record.inSample().sharpeRatio().compareTo(BigDecimal.ZERO) > 0
            && record.outOfSample().sharpeRatio().compareTo(BigDecimal.ZERO) > 0
            && record.outOfSample().totalTrades() > 0
            && record.walkForward().isPassed();
    }

    private static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Path resolveOutputPath() {
        String configured = System.getProperty("strategyCatalogAudit.output");
        if (configured == null || configured.isBlank()) {
            return Path.of("build", "reports", "strategy-catalog-audit", "report.md");
        }
        return Path.of(configured);
    }

    private record AuditStrategySpec(BacktestAlgorithmType algorithmType, String timeframe, String displaySymbol) {
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
}
