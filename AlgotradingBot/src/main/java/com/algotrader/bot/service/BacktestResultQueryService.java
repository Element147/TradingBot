package com.algotrader.bot.service;

import com.algotrader.bot.controller.BacktestComparisonItemResponse;
import com.algotrader.bot.controller.BacktestComparisonResponse;
import com.algotrader.bot.controller.BacktestDetailsResponse;
import com.algotrader.bot.controller.BacktestEquityPointResponse;
import com.algotrader.bot.controller.BacktestExperimentSummaryResponse;
import com.algotrader.bot.controller.BacktestHistoryPageResponse;
import com.algotrader.bot.controller.BacktestHistoryQuery;
import com.algotrader.bot.controller.BacktestHistoryItemResponse;
import com.algotrader.bot.controller.BacktestStrategyMetricResponse;
import com.algotrader.bot.controller.BacktestSummaryResponse;
import com.algotrader.bot.controller.BacktestSymbolTelemetryResponse;
import com.algotrader.bot.controller.BacktestTelemetryQueryResponse;
import com.algotrader.bot.controller.BacktestTradeSeriesItemResponse;
import com.algotrader.bot.controller.AsyncTaskMonitorResponse;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BacktestResultQueryService {

    private static final int DEFAULT_HISTORY_PAGE = 1;
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 25;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final long QUEUE_TIMEOUT_SECONDS = 120;
    private static final long RUNNING_TIMEOUT_SECONDS = 900;
    private static final String DATASET_UNIVERSE_SYMBOL = "DATASET_UNIVERSE";

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestDatasetRepository backtestDatasetRepository;
    private final BacktestTelemetryService backtestTelemetryService;
    private final BackendOperationMetrics backendOperationMetrics;

    public BacktestResultQueryService(BacktestResultRepository backtestResultRepository,
                                      BacktestDatasetRepository backtestDatasetRepository,
                                      BacktestTelemetryService backtestTelemetryService,
                                      BackendOperationMetrics backendOperationMetrics) {
        this.backtestResultRepository = backtestResultRepository;
        this.backtestDatasetRepository = backtestDatasetRepository;
        this.backtestTelemetryService = backtestTelemetryService;
        this.backendOperationMetrics = backendOperationMetrics;
    }

    @Transactional(readOnly = true)
    public BacktestHistoryPageResponse getHistory(BacktestHistoryQuery query) {
        long startedAt = System.nanoTime();
        SanitizedHistoryQuery resolvedQuery = sanitizeQuery(query);
        PageRequest pageRequest = PageRequest.of(
            resolvedQuery.page() - 1,
            resolvedQuery.pageSize(),
            resolvedQuery.sort()
        );
        Page<BacktestResult> historyPage = backtestResultRepository.findAll(
            buildHistorySpecification(resolvedQuery),
            pageRequest
        );
        List<BacktestHistoryItemResponse> history = historyPage.getContent().stream()
            .map(this::toHistoryItem)
            .toList();
        backendOperationMetrics.record(
            "read",
            "backtest_history",
            "page",
            System.nanoTime() - startedAt,
            history.size(),
            0L
        );
        return new BacktestHistoryPageResponse(
            history,
            historyPage.getTotalElements(),
            resolvedQuery.page(),
            resolvedQuery.pageSize()
        );
    }

    @Transactional(readOnly = true)
    public List<BacktestExperimentSummaryResponse> getExperimentSummaries() {
        return backtestResultRepository.findExperimentSummaries().stream()
            .map(summary -> new BacktestExperimentSummaryResponse(
                summary.getExperimentKey(),
                summary.getExperimentName(),
                summary.getLatestBacktestId(),
                summary.getStrategyId(),
                summary.getDatasetName(),
                summary.getSymbol(),
                summary.getTimeframe(),
                summary.getExecutionStatus(),
                summary.getValidationStatus(),
                Math.toIntExact(summary.getRunCount()),
                summary.getLatestRunAt(),
                summary.getAverageReturnPercent(),
                summary.getBestFinalBalance(),
                summary.getWorstMaxDrawdown()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public BacktestDetailsResponse getDetails(Long backtestId) {
        long startedAt = System.nanoTime();
        BacktestResult result = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

        BacktestDetailsResponse details = toDetails(result);
        backendOperationMetrics.record(
            "read",
            "backtest_details",
            "overview",
            System.nanoTime() - startedAt,
            details.availableTelemetrySymbols().size(),
            0L
        );
        return details;
    }

    @Transactional(readOnly = true)
    public BacktestSummaryResponse getSummary(Long backtestId) {
        long startedAt = System.nanoTime();
        BacktestResult result = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));
        BacktestSummaryResponse summary = toSummary(result);
        backendOperationMetrics.record("read", "backtest_summary", "single", System.nanoTime() - startedAt, 1, 0L);
        return summary;
    }

    @Transactional(readOnly = true)
    public List<BacktestEquityPointResponse> getEquityCurve(Long backtestId) {
        long startedAt = System.nanoTime();
        BacktestResult result = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));
        List<BacktestEquityPointResponse> equityCurve = result.getEquityPoints().stream()
            .map(point -> new BacktestEquityPointResponse(
                point.getPointTimestamp(),
                point.getEquity(),
                point.getDrawdownPct()
            ))
            .toList();
        backendOperationMetrics.record(
            "read",
            "backtest_equity_curve",
            "series",
            System.nanoTime() - startedAt,
            equityCurve.size(),
            0L
        );
        return equityCurve;
    }

    @Transactional(readOnly = true)
    public List<BacktestTradeSeriesItemResponse> getTradeSeries(Long backtestId) {
        long startedAt = System.nanoTime();
        BacktestResult result = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));
        List<BacktestTradeSeriesItemResponse> tradeSeries = result.getTradeSeries().stream()
            .map(item -> new BacktestTradeSeriesItemResponse(
                item.getSymbol(),
                item.getPositionSide().name(),
                item.getEntryTime(),
                item.getExitTime(),
                item.getEntryPrice(),
                item.getExitPrice(),
                item.getQuantity(),
                item.getEntryValue(),
                item.getExitValue(),
                item.getReturnPct()
            ))
            .toList();
        backendOperationMetrics.record(
            "read",
            "backtest_trade_series",
            "series",
            System.nanoTime() - startedAt,
            tradeSeries.size(),
            0L
        );
        return tradeSeries;
    }

    @Transactional(readOnly = true)
    public BacktestTelemetryQueryResponse getTelemetry(Long backtestId, String requestedSymbol) {
        long startedAt = System.nanoTime();
        BacktestResult result = backtestResultRepository.findById(backtestId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest not found: " + backtestId));

        List<String> availableSymbols = resolveTelemetrySymbols(result);
        BacktestSymbolTelemetryResponse telemetry = backtestTelemetryService.buildTelemetry(result, requestedSymbol)
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Telemetry not available for backtest: " + backtestId));

        LinkedHashSet<String> resolvedSymbols = new LinkedHashSet<>(availableSymbols);
        resolvedSymbols.add(telemetry.symbol());

        int payloadItems = telemetry.points().size()
            + telemetry.actions().size()
            + telemetry.indicators().stream().mapToInt(indicator -> indicator.points().size()).sum();
        backendOperationMetrics.record(
            "read",
            "backtest_telemetry",
            "symbol_response",
            System.nanoTime() - startedAt,
            payloadItems,
            0L
        );
        return new BacktestTelemetryQueryResponse(
            requestedSymbol,
            telemetry.symbol(),
            List.copyOf(resolvedSymbols),
            telemetry
        );
    }

    @Transactional(readOnly = true)
    public BacktestComparisonResponse compareBacktests(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.size() < 2) {
            throw new IllegalArgumentException("At least two backtest IDs are required for comparison");
        }

        List<Long> orderedIds = requestedIds.stream()
            .filter(id -> id != null && id > 0)
            .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf));

        if (orderedIds.size() < 2) {
            throw new IllegalArgumentException("At least two unique valid backtest IDs are required for comparison");
        }
        if (orderedIds.size() > 10) {
            throw new IllegalArgumentException("Comparison supports up to 10 backtests at once");
        }

        List<BacktestResult> fetched = backtestResultRepository.findAllById(orderedIds);
        Map<Long, BacktestResult> resultById = fetched.stream()
            .collect(Collectors.toMap(BacktestResult::getId, Function.identity()));

        List<Long> missingIds = orderedIds.stream()
            .filter(id -> !resultById.containsKey(id))
            .toList();
        if (!missingIds.isEmpty()) {
            throw new EntityNotFoundException("Backtests not found: " + missingIds);
        }

        List<BacktestResult> orderedResults = orderedIds.stream()
            .map(resultById::get)
            .toList();
        Map<Long, BacktestDataset> datasetById = backtestDatasetRepository.findAllById(
                orderedResults.stream()
                    .map(BacktestResult::getDatasetId)
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new))
            ).stream()
            .collect(Collectors.toMap(BacktestDataset::getId, Function.identity()));

        BacktestResult baseline = orderedResults.get(0);
        BigDecimal baselineReturnPercent = totalReturnPercent(baseline);

        List<BacktestComparisonItemResponse> items = orderedResults.stream()
            .map(result -> {
                BigDecimal returnPercent = totalReturnPercent(result);
                DatasetProvenance provenance = datasetProvenance(result, datasetById);
                return new BacktestComparisonItemResponse(
                    result.getId(),
                    result.getStrategyId(),
                    result.getDatasetName(),
                    provenance.checksumSha256(),
                    provenance.schemaVersion(),
                    provenance.uploadedAt(),
                    provenance.archived(),
                    result.getSymbol(),
                    result.getTimeframe(),
                    result.getExecutionStatus().name(),
                    result.getValidationStatus().name(),
                    result.getFeesBps(),
                    result.getSlippageBps(),
                    result.getTimestamp(),
                    result.getInitialBalance(),
                    result.getFinalBalance(),
                    returnPercent,
                    result.getSharpeRatio(),
                    result.getProfitFactor(),
                    result.getWinRate(),
                    result.getMaxDrawdown(),
                    result.getTotalTrades(),
                    result.getFinalBalance().subtract(baseline.getFinalBalance()),
                    returnPercent.subtract(baselineReturnPercent)
                );
            })
            .toList();

        return new BacktestComparisonResponse(baseline.getId(), items);
    }

    private BacktestHistoryItemResponse toHistoryItem(BacktestResult result) {
        return new BacktestHistoryItemResponse(
            result.getId(),
            result.getStrategyId(),
            result.getDatasetName(),
            resolveExperimentName(result),
            result.getSymbol(),
            result.getTimeframe(),
            result.getExecutionStatus().name(),
            result.getValidationStatus().name(),
            result.getFeesBps(),
            result.getSlippageBps(),
            result.getTimestamp(),
            result.getInitialBalance(),
            result.getFinalBalance(),
            result.getExecutionStage().name(),
            result.getProgressPercent(),
            result.getProcessedCandles(),
            result.getTotalCandles(),
            result.getCurrentDataTimestamp(),
            result.getStatusMessage(),
            result.getLastProgressAt(),
            result.getStartedAt(),
            result.getCompletedAt(),
            buildAsyncMonitor(result)
        );
    }

    private BacktestDetailsResponse toDetails(BacktestResult result) {
        BacktestSummaryResponse summary = toSummary(result);
        return new BacktestDetailsResponse(
            summary.id(),
            summary.strategyId(),
            summary.datasetId(),
            summary.datasetName(),
            summary.experimentName(),
            summary.experimentKey(),
            summary.datasetChecksumSha256(),
            summary.datasetSchemaVersion(),
            summary.datasetUploadedAt(),
            summary.datasetArchived(),
            summary.symbol(),
            summary.timeframe(),
            summary.executionStatus(),
            summary.validationStatus(),
            summary.initialBalance(),
            summary.finalBalance(),
            summary.sharpeRatio(),
            summary.profitFactor(),
            summary.winRate(),
            summary.maxDrawdown(),
            summary.totalTrades(),
            summary.feesBps(),
            summary.slippageBps(),
            summary.startDate(),
            summary.endDate(),
            summary.timestamp(),
            summary.executionStage(),
            summary.progressPercent(),
            summary.processedCandles(),
            summary.totalCandles(),
            summary.currentDataTimestamp(),
            summary.statusMessage(),
            summary.lastProgressAt(),
            summary.startedAt(),
            summary.completedAt(),
            summary.errorMessage(),
            summary.strategyMetrics(),
            resolveTelemetrySymbols(result),
            summary.asyncMonitor()
        );
    }

    private BacktestSummaryResponse toSummary(BacktestResult result) {
        DatasetProvenance provenance = datasetProvenance(result);
        return new BacktestSummaryResponse(
            result.getId(),
            result.getStrategyId(),
            result.getDatasetId(),
            result.getDatasetName(),
            resolveExperimentName(result),
            resolveExperimentKey(result),
            provenance.checksumSha256(),
            provenance.schemaVersion(),
            provenance.uploadedAt(),
            provenance.archived(),
            result.getSymbol(),
            result.getTimeframe(),
            result.getExecutionStatus().name(),
            result.getValidationStatus().name(),
            result.getInitialBalance(),
            result.getFinalBalance(),
            result.getSharpeRatio(),
            result.getProfitFactor(),
            result.getWinRate(),
            result.getMaxDrawdown(),
            result.getTotalTrades(),
            result.getFeesBps(),
            result.getSlippageBps(),
            result.getStartDate(),
            result.getEndDate(),
            result.getTimestamp(),
            result.getExecutionStage().name(),
            result.getProgressPercent(),
            result.getProcessedCandles(),
            result.getTotalCandles(),
            result.getCurrentDataTimestamp(),
            result.getStatusMessage(),
            result.getLastProgressAt(),
            result.getStartedAt(),
            result.getCompletedAt(),
            result.getErrorMessage(),
            strategyMetrics(result),
            buildAsyncMonitor(result)
        );
    }

    private AsyncTaskMonitorResponse buildAsyncMonitor(BacktestResult result) {
        String state = switch (result.getExecutionStatus()) {
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case RUNNING -> "RUNNING";
            case PENDING -> "QUEUED";
        };
        long timeoutThresholdSeconds = result.getExecutionStatus() == BacktestResult.ExecutionStatus.RUNNING
            ? RUNNING_TIMEOUT_SECONDS
            : QUEUE_TIMEOUT_SECONDS;
        LocalDateTime heartbeatBase = result.getLastProgressAt() != null
            ? result.getLastProgressAt()
            : result.getTimestamp();
        boolean timedOut = (result.getExecutionStatus() == BacktestResult.ExecutionStatus.PENDING
            || result.getExecutionStatus() == BacktestResult.ExecutionStatus.RUNNING)
            && heartbeatBase != null
            && Duration.between(heartbeatBase, LocalDateTime.now()).getSeconds() > timeoutThresholdSeconds;

        return new AsyncTaskMonitorResponse(
            state,
            result.getStartedAt() == null && result.getCompletedAt() == null ? 0 : 1,
            1,
            null,
            result.getExecutionStatus() == BacktestResult.ExecutionStatus.FAILED
                || result.getExecutionStatus() == BacktestResult.ExecutionStatus.COMPLETED,
            timedOut,
            timeoutThresholdSeconds
        );
    }

    private BigDecimal totalReturnPercent(BacktestResult result) {
        if (result.getInitialBalance() == null
            || result.getFinalBalance() == null
            || result.getInitialBalance().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return result.getFinalBalance()
            .subtract(result.getInitialBalance())
            .divide(result.getInitialBalance(), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(4, RoundingMode.HALF_UP);
    }

    private List<BacktestStrategyMetricResponse> strategyMetrics(BacktestResult result) {
        if (!BacktestAlgorithmType.SQUEEZE_BREAKOUT_REGIME_CONFIRMATION.name().equalsIgnoreCase(result.getStrategyId())) {
            return List.of();
        }

        List<BacktestTradeSeriesItem> trades = result.getTradeSeries();
        if (trades.isEmpty()) {
            return List.of(
                new BacktestStrategyMetricResponse(
                    "breakout_failure_rate",
                    "Breakout failure rate",
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "0.00%",
                    "Share of completed breakout trades that finished at or below zero return."
                ),
                new BacktestStrategyMetricResponse(
                    "average_hold_hours",
                    "Average hold",
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "0.00h",
                    "Average completed trade duration measured in hours."
                )
            );
        }

        long failedTrades = trades.stream()
            .filter(item -> item.getReturnPct() == null || item.getReturnPct().compareTo(BigDecimal.ZERO) <= 0)
            .count();
        BigDecimal failureRate = BigDecimal.valueOf(failedTrades)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalHoldHours = trades.stream()
            .map(item -> BigDecimal.valueOf(Duration.between(item.getEntryTime(), item.getExitTime()).toMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageHoldHours = totalHoldHours
            .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP);

        List<BacktestStrategyMetricResponse> metrics = new ArrayList<>();
        metrics.add(new BacktestStrategyMetricResponse(
            "breakout_failure_rate",
            "Breakout failure rate",
            failureRate,
            failureRate.toPlainString() + "%",
            "Share of completed breakout trades that finished at or below zero return."
        ));
        metrics.add(new BacktestStrategyMetricResponse(
            "average_hold_hours",
            "Average hold",
            averageHoldHours,
            averageHoldHours.toPlainString() + "h",
            "Average completed trade duration measured in hours."
        ));
        return List.copyOf(metrics);
    }

    private String resolveExperimentName(BacktestResult result) {
        if (result.getExperimentName() != null && !result.getExperimentName().isBlank()) {
            return result.getExperimentName();
        }

        String datasetLabel = result.getDatasetName() == null || result.getDatasetName().isBlank()
            ? "dataset-" + (result.getDatasetId() == null ? "unknown" : result.getDatasetId())
            : result.getDatasetName();
        return result.getStrategyId() + " | " + datasetLabel + " | " + result.getSymbol() + " | " + result.getTimeframe();
    }

    private String resolveExperimentKey(BacktestResult result) {
        if (result.getExperimentKey() != null && !result.getExperimentKey().isBlank()) {
            return result.getExperimentKey();
        }
        return normalizeExperimentKey(resolveExperimentName(result));
    }

    private String normalizeExperimentKey(String experimentName) {
        return experimentName
            .trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    private DatasetProvenance datasetProvenance(BacktestResult result) {
        if (result.getDatasetId() == null) {
            return DatasetProvenance.empty();
        }

        return backtestDatasetRepository.findById(result.getDatasetId())
            .map(BacktestResultQueryService::toDatasetProvenance)
            .orElseGet(DatasetProvenance::empty);
    }

    private DatasetProvenance datasetProvenance(BacktestResult result, Map<Long, BacktestDataset> datasetById) {
        if (result.getDatasetId() == null) {
            return DatasetProvenance.empty();
        }

        return toDatasetProvenance(datasetById.get(result.getDatasetId()));
    }

    private static DatasetProvenance toDatasetProvenance(BacktestDataset dataset) {
        if (dataset == null) {
            return DatasetProvenance.empty();
        }

        return new DatasetProvenance(
            dataset.getChecksumSha256(),
            dataset.getSchemaVersion(),
            dataset.getUploadedAt(),
            dataset.getArchived()
        );
    }

    private SanitizedHistoryQuery sanitizeQuery(BacktestHistoryQuery query) {
        int page = query == null || query.page() == null || query.page() <= 0
            ? DEFAULT_HISTORY_PAGE
            : query.page();
        int pageSize = query == null || query.pageSize() == null || query.pageSize() <= 0
            ? DEFAULT_HISTORY_PAGE_SIZE
            : Math.min(query.pageSize(), MAX_HISTORY_PAGE_SIZE);
        Sort.Direction direction = parseSortDirection(query == null ? null : query.sortDirection());
        Sort sort = resolveSort(query == null ? null : query.sortBy(), direction);

        return new SanitizedHistoryQuery(
            page,
            pageSize,
            sort,
            normalizeFilter(query == null ? null : query.search()),
            normalizeFilter(query == null ? null : query.strategyId()),
            normalizeFilter(query == null ? null : query.datasetName()),
            normalizeFilter(query == null ? null : query.experimentName()),
            normalizeFilter(query == null ? null : query.market()),
            normalizeEnumFilter(query == null ? null : query.executionStatus()),
            normalizeEnumFilter(query == null ? null : query.validationStatus()),
            query == null ? null : query.feesBpsMin(),
            query == null ? null : query.feesBpsMax(),
            query == null ? null : query.slippageBpsMin(),
            query == null ? null : query.slippageBpsMax()
        );
    }

    private Specification<BacktestResult> buildHistorySpecification(SanitizedHistoryQuery query) {
        Specification<BacktestResult> specification = null;

        if (query.search() != null) {
            specification = appendSpecification(specification, globalSearch(query.search()));
        }
        if (query.strategyId() != null) {
            specification = appendSpecification(specification, containsIgnoreCase("strategyId", query.strategyId()));
        }
        if (query.datasetName() != null) {
            specification = appendSpecification(specification, containsIgnoreCase("datasetName", query.datasetName()));
        }
        if (query.experimentName() != null) {
            specification = appendSpecification(specification, containsIgnoreCase("experimentName", query.experimentName()));
        }
        if (query.market() != null) {
            specification = appendSpecification(specification, marketContains(query.market()));
        }
        if (query.executionStatus() != null) {
            specification = appendSpecification(specification, exactMatch("executionStatus", query.executionStatus()));
        }
        if (query.validationStatus() != null) {
            specification = appendSpecification(specification, exactMatch("validationStatus", query.validationStatus()));
        }
        if (query.feesBpsMin() != null) {
            specification = appendSpecification(specification, greaterThanOrEqualTo("feesBps", query.feesBpsMin()));
        }
        if (query.feesBpsMax() != null) {
            specification = appendSpecification(specification, lessThanOrEqualTo("feesBps", query.feesBpsMax()));
        }
        if (query.slippageBpsMin() != null) {
            specification = appendSpecification(specification, greaterThanOrEqualTo("slippageBps", query.slippageBpsMin()));
        }
        if (query.slippageBpsMax() != null) {
            specification = appendSpecification(specification, lessThanOrEqualTo("slippageBps", query.slippageBpsMax()));
        }

        return specification;
    }

    private Specification<BacktestResult> appendSpecification(Specification<BacktestResult> specification,
                                                              Specification<BacktestResult> addition) {
        return specification == null ? addition : specification.and(addition);
    }

    private Specification<BacktestResult> globalSearch(String search) {
        String likeValue = containsPattern(search);
        boolean searchLooksNumeric = search.chars().allMatch(Character::isDigit);

        return (root, ignoredQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.like(lowerText(root.get("strategyId"), criteriaBuilder), likeValue));
            predicates.add(criteriaBuilder.like(lowerText(root.get("datasetName"), criteriaBuilder), likeValue));
            predicates.add(criteriaBuilder.like(lowerText(root.get("experimentName"), criteriaBuilder), likeValue));
            predicates.add(criteriaBuilder.like(lowerText(root.get("symbol"), criteriaBuilder), likeValue));
            predicates.add(criteriaBuilder.like(lowerText(root.get("timeframe"), criteriaBuilder), likeValue));
            if (searchLooksNumeric) {
                predicates.add(criteriaBuilder.equal(root.get("id"), Long.parseLong(search)));
            }
            return criteriaBuilder.or(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Specification<BacktestResult> containsIgnoreCase(String field, String value) {
        String likeValue = containsPattern(value);
        return (root, ignoredQuery, criteriaBuilder) ->
            criteriaBuilder.like(lowerText(root.get(field), criteriaBuilder), likeValue);
    }

    private Specification<BacktestResult> marketContains(String value) {
        String likeValue = containsPattern(value);
        boolean wholeDatasetUniverseQuery = value.contains("whole dataset universe");

        return (root, ignoredQuery, criteriaBuilder) -> {
            Expression<String> marketExpression = criteriaBuilder.concat(
                criteriaBuilder.concat(lowerText(root.get("symbol"), criteriaBuilder), " "),
                lowerText(root.get("timeframe"), criteriaBuilder)
            );

            if (!wholeDatasetUniverseQuery) {
                return criteriaBuilder.like(marketExpression, likeValue);
            }

            return criteriaBuilder.or(
                criteriaBuilder.like(marketExpression, likeValue),
                criteriaBuilder.equal(root.get("symbol"), DATASET_UNIVERSE_SYMBOL)
            );
        };
    }

    private Specification<BacktestResult> exactMatch(String field, String value) {
        return (root, ignoredQuery, criteriaBuilder) ->
            criteriaBuilder.equal(criteriaBuilder.upper(root.get(field)), value);
    }

    private Specification<BacktestResult> greaterThanOrEqualTo(String field, Integer value) {
        return (root, ignoredQuery, criteriaBuilder) ->
            criteriaBuilder.greaterThanOrEqualTo(root.get(field), value);
    }

    private Specification<BacktestResult> lessThanOrEqualTo(String field, Integer value) {
        return (root, ignoredQuery, criteriaBuilder) ->
            criteriaBuilder.lessThanOrEqualTo(root.get(field), value);
    }

    private Expression<String> lowerText(Expression<String> expression,
                                         jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(expression, ""));
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEnumFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String containsPattern(String value) {
        return "%" + value + "%";
    }

    private Sort.Direction parseSortDirection(String value) {
        return "asc".equalsIgnoreCase(value) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private Sort resolveSort(String requestedSortBy, Sort.Direction direction) {
        if (requestedSortBy == null || requestedSortBy.isBlank()) {
            return Sort.by(direction, "timestamp").and(Sort.by(direction, "id"));
        }

        return switch (requestedSortBy.trim()) {
            case "id" -> Sort.by(direction, "id");
            case "strategyId" -> Sort.by(direction, "strategyId").and(Sort.by(direction, "timestamp"));
            case "datasetName" -> Sort.by(direction, "datasetName").and(Sort.by(direction, "timestamp"));
            case "experimentName" -> Sort.by(direction, "experimentName").and(Sort.by(direction, "timestamp"));
            case "market" -> Sort.by(direction, "symbol").and(Sort.by(direction, "timeframe"));
            case "executionStatus" -> Sort.by(direction, "executionStatus").and(Sort.by(direction, "timestamp"));
            case "validationStatus" -> Sort.by(direction, "validationStatus").and(Sort.by(direction, "timestamp"));
            case "feesBps" -> Sort.by(direction, "feesBps").and(Sort.by(direction, "timestamp"));
            case "slippageBps" -> Sort.by(direction, "slippageBps").and(Sort.by(direction, "timestamp"));
            case "timestamp" -> Sort.by(direction, "timestamp").and(Sort.by(direction, "id"));
            default -> Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }

    private List<String> resolveTelemetrySymbols(BacktestResult result) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        if (result.getSymbol() != null
            && !result.getSymbol().isBlank()
            && !"DATASET_UNIVERSE".equalsIgnoreCase(result.getSymbol())) {
            symbols.add(result.getSymbol());
        }
        result.getTradeSeries().stream()
            .map(item -> item.getSymbol())
            .filter(Objects::nonNull)
            .filter(symbol -> !symbol.isBlank())
            .forEach(symbols::add);
        return List.copyOf(symbols);
    }

    private record DatasetProvenance(
        String checksumSha256,
        String schemaVersion,
        LocalDateTime uploadedAt,
        Boolean archived
    ) {
        private static DatasetProvenance empty() {
            return new DatasetProvenance(null, null, null, null);
        }
    }

    private record SanitizedHistoryQuery(
        int page,
        int pageSize,
        Sort sort,
        String search,
        String strategyId,
        String datasetName,
        String experimentName,
        String market,
        String executionStatus,
        String validationStatus,
        Integer feesBpsMin,
        Integer feesBpsMax,
        Integer slippageBpsMin,
        Integer slippageBpsMax
    ) {}
}
