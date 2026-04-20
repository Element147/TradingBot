package com.algotrader.bot.repository;

import com.algotrader.bot.entity.BacktestResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

/**
 * Spring Data JPA repository for BacktestResult entity.
 * Provides CRUD operations and custom query methods for backtest result retrieval.
 */
@Repository
public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long>, JpaSpecificationExecutor<BacktestResult> {
    List<BacktestResult> findAllByOrderByTimestampDesc();
    List<BacktestResult> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Find all backtest results for a specific strategy.
     *
     * @param strategyId the strategy identifier
     * @return list of backtest results for the strategy
     */
    List<BacktestResult> findByStrategyId(String strategyId);
    List<BacktestResult> findByStrategyIdOrderByTimestampDesc(String strategyId, Pageable pageable);

    /**
     * Find all backtest results for a specific symbol.
     *
     * @param symbol the trading pair symbol (e.g., "BTC/USDT")
     * @return list of backtest results for the symbol
     */
    List<BacktestResult> findBySymbol(String symbol);
    List<BacktestResult> findBySymbolOrderByTimestampDesc(String symbol, Pageable pageable);

    /**
     * Find all backtest results with a specific validation status.
     *
     * @param status the validation status (PENDING, PASSED, FAILED, PRODUCTION_READY)
     * @return list of backtest results with the specified status
     */
    List<BacktestResult> findByValidationStatus(BacktestResult.ValidationStatus status);
    List<BacktestResult> findByExecutionStatusInOrderByTimestampAsc(Collection<BacktestResult.ExecutionStatus> statuses);

    /**
     * Find all backtest results for a specific strategy and symbol.
     *
     * @param strategyId the strategy identifier
     * @param symbol the trading pair symbol
     * @return list of backtest results matching both criteria
     */
    List<BacktestResult> findByStrategyIdAndSymbol(String strategyId, String symbol);
    List<BacktestResult> findByStrategyIdAndSymbolOrderByTimestampDesc(String strategyId, String symbol, Pageable pageable);

    @Query("""
        select r.datasetId as datasetId, count(r) as usageCount, max(r.timestamp) as lastUsedAt
        from BacktestResult r
        where r.datasetId is not null
        group by r.datasetId
        """)
    List<BacktestDatasetUsageSummary> summarizeDatasetUsage();

    @Query(value = """
        with keyed_runs as (
            select
                id,
                dataset_id,
                strategy_id,
                dataset_name,
                symbol,
                timeframe,
                execution_status,
                validation_status,
                "timestamp" as run_timestamp,
                initial_balance,
                final_balance,
                max_drawdown,
                coalesce(
                    nullif(trim(experiment_key), ''),
                    lower(
                        replace(
                            replace(
                                replace(
                                    replace(
                                        replace(
                                            coalesce(
                                                nullif(trim(experiment_name), ''),
                                                strategy_id || ' | ' || coalesce(dataset_name, 'dataset-' || coalesce(cast(dataset_id as varchar), 'unknown')) || ' | ' || symbol || ' | ' || timeframe
                                            ),
                                            ' | ',
                                            '-'
                                        ),
                                        ' ',
                                        '-'
                                    ),
                                    '/',
                                    '-'
                                ),
                                '_',
                                '-'
                            ),
                            '--',
                            '-'
                        )
                    )
                ) as normalized_experiment_key,
                coalesce(
                    nullif(trim(experiment_name), ''),
                    strategy_id || ' | ' || coalesce(dataset_name, 'dataset-' || coalesce(cast(dataset_id as varchar), 'unknown')) || ' | ' || symbol || ' | ' || timeframe
                ) as resolved_experiment_name
            from backtest_results
        ),
        aggregated as (
            select
                normalized_experiment_key,
                count(*) as run_count,
                avg(
                    case
                        when initial_balance is null or initial_balance = 0 or final_balance is null then 0
                        else ((final_balance - initial_balance) / initial_balance) * 100
                    end
                ) as average_return_percent,
                max(final_balance) as best_final_balance,
                max(max_drawdown) as worst_max_drawdown
            from keyed_runs
            group by normalized_experiment_key
        ),
        ranked as (
            select
                *,
                row_number() over (
                    partition by normalized_experiment_key
                    order by run_timestamp desc, id desc
                ) as row_num
            from keyed_runs
        )
        select
            ranked.normalized_experiment_key as experimentKey,
            ranked.resolved_experiment_name as experimentName,
            ranked.id as latestBacktestId,
            ranked.strategy_id as strategyId,
            ranked.dataset_name as datasetName,
            ranked.symbol as symbol,
            ranked.timeframe as timeframe,
            ranked.execution_status as executionStatus,
            ranked.validation_status as validationStatus,
            aggregated.run_count as runCount,
            ranked.run_timestamp as latestRunAt,
            cast(round(aggregated.average_return_percent, 4) as decimal(20,4)) as averageReturnPercent,
            aggregated.best_final_balance as bestFinalBalance,
            aggregated.worst_max_drawdown as worstMaxDrawdown
        from ranked
        join aggregated on aggregated.normalized_experiment_key = ranked.normalized_experiment_key
        where ranked.row_num = 1
        order by ranked.run_timestamp desc, ranked.id desc
        """, nativeQuery = true)
    List<BacktestExperimentSummaryProjection> findExperimentSummaries();
}
