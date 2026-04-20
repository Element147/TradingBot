import DownloadIcon from '@mui/icons-material/Download';
import { Alert, Button, Stack, Typography } from '@mui/material';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import { useMemo } from 'react';

import type { BacktestComparisonItem, BacktestComparisonResponse } from './backtestApi';
import { BacktestComparisonCharts } from './BacktestComparisonCharts';
import { formatBacktestMarketLabel } from './backtestTypes';

import {
  InteractiveTable,
  useInteractiveTableState,
} from '@/components/ui/InteractiveTable';
import { NumericText, StatusPill } from '@/components/ui/Workbench';
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatPercentage,
} from '@/utils/formatters';

interface BacktestComparisonPanelProps {
  comparison: BacktestComparisonResponse;
}

const deltaTone = (value: number): 'success' | 'error' | 'default' => {
  if (value > 0) {
    return 'success';
  }
  if (value < 0) {
    return 'error';
  }
  return 'default';
};

const getComparableValue = (item: BacktestComparisonItem, columnId: string): string | number => {
  switch (columnId) {
    case 'timestamp':
      return new Date(item.timestamp).getTime();
    case 'datasetProof':
      return `${item.datasetSchemaVersion ?? ''} ${item.datasetChecksumSha256 ?? ''}`;
    case 'market':
      return `${item.symbol} ${item.timeframe}`;
    case 'id':
      return item.id;
    case 'finalBalance':
      return item.finalBalance;
    case 'finalBalanceDelta':
      return item.finalBalanceDelta;
    case 'totalReturnPercent':
      return item.totalReturnPercent;
    case 'totalReturnDeltaPercent':
      return item.totalReturnDeltaPercent;
    case 'sharpeRatio':
      return item.sharpeRatio;
    case 'profitFactor':
      return item.profitFactor;
    case 'winRate':
      return item.winRate;
    case 'maxDrawdown':
      return item.maxDrawdown;
    case 'totalTrades':
      return item.totalTrades;
    default:
      return String(item[columnId as keyof BacktestComparisonItem] ?? '');
  }
};

const compareItems = (
  left: BacktestComparisonItem,
  right: BacktestComparisonItem,
  sorting: SortingState
) => {
  for (const rule of sorting) {
    const leftValue = getComparableValue(left, rule.id);
    const rightValue = getComparableValue(right, rule.id);

    const comparison =
      typeof leftValue === 'number' && typeof rightValue === 'number'
        ? leftValue - rightValue
        : String(leftValue).localeCompare(String(rightValue), undefined, {
            sensitivity: 'base',
          });

    if (comparison !== 0) {
      return rule.desc ? comparison * -1 : comparison;
    }
  }

  return 0;
};

export function BacktestComparisonPanel({ comparison }: BacktestComparisonPanelProps) {
  const tableStateControls = useInteractiveTableState({
    tableId: 'backtest-comparison',
    initialPageSize: 10,
  });
  const hasCompleteProvenance = comparison.items.every(
    (item) => item.datasetChecksumSha256 && item.datasetSchemaVersion && item.datasetUploadedAt
  );
  const uniqueChecksums = new Set(
    comparison.items
      .map((item) => item.datasetChecksumSha256)
      .filter((checksum): checksum is string => Boolean(checksum))
  );
  const uniqueSchemas = new Set(
    comparison.items
      .map((item) => item.datasetSchemaVersion)
      .filter((schema): schema is string => Boolean(schema))
  );
  const mixedDatasetInputs = uniqueChecksums.size > 1 || uniqueSchemas.size > 1;

  const filteredItems = useMemo(() => {
    const baseline =
      comparison.items.find((item) => item.id === comparison.baselineBacktestId) ?? null;
    const others = comparison.items.filter((item) => item.id !== comparison.baselineBacktestId);
    const globalQuery = tableStateControls.state.globalFilter.trim().toLowerCase();
    const columnFilters = new Map(
      tableStateControls.state.columnFilters.map((filter) => [filter.id, String(filter.value).trim().toLowerCase()])
    );

    const matchesFilter = (item: BacktestComparisonItem) => {
      if (globalQuery) {
        const haystack = [
          item.id,
          item.strategyId,
          item.datasetName ?? '',
          item.datasetSchemaVersion ?? '',
          item.datasetChecksumSha256 ?? '',
          item.symbol,
          item.timeframe,
        ]
          .join(' ')
          .toLowerCase();
        if (!haystack.includes(globalQuery)) {
          return false;
        }
      }

      for (const [columnId, value] of columnFilters.entries()) {
        if (!value) {
          continue;
        }

        const rawValue = getComparableValue(item, columnId);
        if (String(rawValue).toLowerCase().includes(value)) {
          continue;
        }

        return false;
      }

      return true;
    };

    const matchingBaseline = baseline && matchesFilter(baseline) ? baseline : null;
    const matchingOthers = others.filter(matchesFilter);
    const sortedOthers =
      tableStateControls.state.sorting.length > 0
        ? [...matchingOthers].sort((left, right) =>
            compareItems(left, right, tableStateControls.state.sorting)
          )
        : matchingOthers;

    return matchingBaseline ? [matchingBaseline, ...sortedOthers] : sortedOthers;
  }, [
    comparison.baselineBacktestId,
    comparison.items,
    tableStateControls.state.columnFilters,
    tableStateControls.state.globalFilter,
    tableStateControls.state.sorting,
  ]);

  const columns = useMemo<ColumnDef<BacktestComparisonItem>[]>(
    () => [
      {
        accessorKey: 'id',
        header: 'Run',
        size: 88,
        minSize: 88,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Unique run identifier.',
        },
        cell: ({ row }) => (
          <Stack spacing={0.35}>
            <NumericText variant="body2">#{row.original.id}</NumericText>
            {row.original.id === comparison.baselineBacktestId ? (
              <StatusPill label="Baseline" tone="info" variant="filled" />
            ) : null}
          </Stack>
        ),
      },
      {
        accessorKey: 'strategyId',
        header: 'Strategy',
        size: 200,
        minSize: 170,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Strategy',
          headerDescription: 'Strategy logic under comparison.',
        },
      },
      {
        accessorKey: 'datasetName',
        header: 'Dataset',
        size: 180,
        minSize: 160,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Dataset',
          headerDescription: 'Dataset label used by the run.',
        },
      },
      {
        id: 'datasetProof',
        header: 'Dataset proof',
        accessorFn: (row) => `${row.datasetSchemaVersion ?? ''} ${row.datasetChecksumSha256 ?? ''}`,
        size: 220,
        minSize: 200,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Schema',
          headerDescription: 'Schema version and checksum used for reproducibility.',
        },
        cell: ({ row }) =>
          row.original.datasetSchemaVersion && row.original.datasetChecksumSha256 ? (
            <Stack spacing={0.35}>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                {row.original.datasetSchemaVersion}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {row.original.datasetChecksumSha256.slice(0, 16)}...
              </Typography>
            </Stack>
          ) : (
            <StatusPill label="Missing" tone="warning" variant="filled" />
          ),
      },
      {
        id: 'market',
        header: 'Market',
        accessorFn: (row) => `${row.symbol} ${row.timeframe}`,
        size: 170,
        minSize: 150,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Market',
          headerDescription: 'Symbol and timeframe compared in the run.',
        },
        cell: ({ row }) => (
          <Typography variant="body2">
            {formatBacktestMarketLabel(row.original.symbol)} ({row.original.timeframe})
          </Typography>
        ),
      },
      {
        accessorKey: 'timestamp',
        header: 'Finished',
        size: 154,
        minSize: 140,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Completion timestamp for this comparison row.',
        },
        cell: ({ row }) => <Typography variant="body2">{formatDateTime(row.original.timestamp)}</Typography>,
      },
      {
        accessorKey: 'finalBalance',
        header: 'Final balance',
        size: 136,
        minSize: 120,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Ending balance under the selected cost assumptions.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatCurrency(row.original.finalBalance)}</NumericText>,
      },
      {
        accessorKey: 'finalBalanceDelta',
        header: 'Balance delta',
        size: 140,
        minSize: 124,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Delta versus the baseline run balance.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2" tone={deltaTone(row.original.finalBalanceDelta)}>
            {formatCurrency(row.original.finalBalanceDelta)}
          </NumericText>
        ),
      },
      {
        accessorKey: 'totalReturnPercent',
        header: 'Return',
        size: 116,
        minSize: 100,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Total return percentage for the run.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2">{formatPercentage(row.original.totalReturnPercent, 2)}</NumericText>
        ),
      },
      {
        accessorKey: 'totalReturnDeltaPercent',
        header: 'Return delta',
        size: 128,
        minSize: 112,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Return delta versus the baseline run.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2" tone={deltaTone(row.original.totalReturnDeltaPercent)}>
            {formatPercentage(row.original.totalReturnDeltaPercent, 2)}
          </NumericText>
        ),
      },
      {
        accessorKey: 'sharpeRatio',
        header: 'Sharpe',
        size: 96,
        minSize: 84,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Sharpe ratio for the run.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatNumber(row.original.sharpeRatio, 2)}</NumericText>,
      },
      {
        accessorKey: 'profitFactor',
        header: 'Profit factor',
        size: 120,
        minSize: 104,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Profit factor for the run.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatNumber(row.original.profitFactor, 2)}</NumericText>,
      },
      {
        accessorKey: 'winRate',
        header: 'Win rate',
        size: 108,
        minSize: 96,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Win rate for the run.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatPercentage(row.original.winRate, 2)}</NumericText>,
      },
      {
        accessorKey: 'maxDrawdown',
        header: 'Max DD',
        size: 106,
        minSize: 94,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Maximum drawdown percentage for the run.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2">{formatPercentage(row.original.maxDrawdown, 2)}</NumericText>
        ),
      },
      {
        accessorKey: 'totalTrades',
        header: 'Trades',
        size: 90,
        minSize: 84,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Total number of trades in the run.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatNumber(row.original.totalTrades)}</NumericText>,
      },
    ],
    [comparison.baselineBacktestId]
  );

  const exportCsv = () => {
    if (!hasCompleteProvenance) {
      return;
    }

    const rows = [
      [
        'runId',
        'strategyId',
        'datasetName',
        'datasetSchemaVersion',
        'datasetChecksumSha256',
        'datasetUploadedAt',
        'datasetArchived',
        'finalBalance',
        'finalBalanceDelta',
        'totalReturnPercent',
        'totalReturnDeltaPercent',
        'sharpeRatio',
        'profitFactor',
        'winRate',
        'maxDrawdown',
        'totalTrades',
      ].join(','),
      ...comparison.items.map((item) =>
        [
          item.id,
          item.strategyId,
          item.datasetName ?? '',
          item.datasetSchemaVersion ?? '',
          item.datasetChecksumSha256 ?? '',
          item.datasetUploadedAt ?? '',
          item.datasetArchived ?? '',
          item.finalBalance,
          item.finalBalanceDelta,
          item.totalReturnPercent,
          item.totalReturnDeltaPercent,
          item.sharpeRatio,
          item.profitFactor,
          item.winRate,
          item.maxDrawdown,
          item.totalTrades,
        ]
          .map((value) => `"${String(value).replaceAll('"', '""')}"`)
          .join(',')
      ),
    ].join('\n');

    const blob = new Blob([rows], { type: 'text/csv;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = `backtest-comparison-${comparison.baselineBacktestId}.csv`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  };

  return (
    <Stack spacing={2}>
      <Alert severity="info">
        Baseline run #{comparison.baselineBacktestId} stays pinned to the first row. Delta columns
        show the change versus that baseline even after sorting and filtering the remaining runs.
      </Alert>
      {mixedDatasetInputs ? (
        <Alert severity="warning">
          Comparison spans multiple dataset inputs or schema versions. Treat delta conclusions as
          cross-input research, not like-for-like replay evidence.
        </Alert>
      ) : null}
      {!hasCompleteProvenance ? (
        <Alert severity="warning">
          CSV export is blocked until every selected run includes dataset checksum, schema version,
          and upload timestamp metadata.
        </Alert>
      ) : null}

      <BacktestComparisonCharts comparison={comparison} />

      <InteractiveTable
        title="Backtest comparison"
        description="Comparison now uses the same compact grid shell as history, while preserving the baseline row at the top for easier reasoning about deltas."
        data={filteredItems}
        columns={columns}
        stateControls={tableStateControls}
        manualFiltering
        manualSorting
        emptyTitle="No comparison rows match this filter set"
        emptyDescription="Clear a column filter or the quick search to bring the compared runs back."
        globalFilterPlaceholder="Run ID, strategy, dataset, market, or checksum"
        actions={
          <Button
            variant="outlined"
            size="small"
            startIcon={<DownloadIcon />}
            onClick={exportCsv}
            disabled={!hasCompleteProvenance}
          >
            Export CSV
          </Button>
        }
        stats={
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <StatusPill label={`${comparison.items.length} runs`} tone="info" variant="filled" />
            <StatusPill label={`Baseline #${comparison.baselineBacktestId}`} tone="info" />
            {mixedDatasetInputs ? (
              <StatusPill label="Mixed dataset provenance" tone="warning" variant="filled" />
            ) : (
              <StatusPill label="Single dataset provenance" tone="success" />
            )}
          </Stack>
        }
        getRowId={(row) => String(row.id)}
        isRowSelected={(row) => row.id === comparison.baselineBacktestId}
        maxHeight={640}
      />
    </Stack>
  );
}
