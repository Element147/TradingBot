import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ReplayIcon from '@mui/icons-material/Replay';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import {
  Box,
  Button,
  Checkbox,
  LinearProgress,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';

import type {
  BacktestComparisonResponse,
  BacktestHistoryItem,
  BacktestHistoryResult,
} from './backtestApi';
import { BacktestComparisonPanel } from './BacktestComparisonPanel';
import {
  executionProgressValue,
  executionStageDescription,
  executionStageLabel,
  executionStatusColor,
  formatLastUpdate,
  formatLiveEventTimestamp,
  formatProgressTimestamp,
  isExecutionActive,
} from './backtestPageState';
import { formatBacktestMarketLabel } from './backtestTypes';

import {
  InteractiveTable,
  type InteractiveTableStateControls,
} from '@/components/ui/InteractiveTable';
import { NumericText, StatusPill } from '@/components/ui/Workbench';
import {
  formatDateTime,
  formatNumber,
} from '@/utils/formatters';

interface HistoryRangeFilters {
  feesBpsMin: string;
  feesBpsMax: string;
  slippageBpsMin: string;
  slippageBpsMax: string;
}

interface BacktestHistoryWorkspacePanelProps {
  history: BacktestHistoryResult;
  comparison?: BacktestComparisonResponse;
  comparisonIds: number[];
  selectedId: number | null;
  comparisonIsStale: boolean;
  comparisonErrorMessage: string | null;
  lastLiveEventAt: string | null;
  isLoading: boolean;
  isError: boolean;
  isComparing: boolean;
  isReplaying: boolean;
  isDeletingBacktest: boolean;
  tableStateControls: InteractiveTableStateControls;
  rangeFilters: HistoryRangeFilters;
  onRangeFilterChange: (field: keyof HistoryRangeFilters, value: string) => void;
  onCompareSelected: () => void | Promise<void>;
  onClearComparison: () => void;
  onToggleComparison: (backtestId: number) => void;
  onSelectRun: (backtestId: number) => void;
  onViewDetails: (backtestId: number) => void;
  onReplayBacktest: (backtestId: number) => void | Promise<void>;
  onDeleteResult: (item: BacktestHistoryItem) => void | Promise<void>;
}

const asyncStateLabel = (item: BacktestHistoryItem) =>
  item.asyncMonitor?.state ?? (item.executionStatus === 'PENDING' ? 'QUEUED' : item.executionStatus);

const validationTone = (
  status: BacktestHistoryItem['validationStatus']
): 'success' | 'warning' | 'error' | 'default' => {
  if (status === 'PASSED' || status === 'PRODUCTION_READY') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'error';
  }
  return 'warning';
};

export function BacktestHistoryWorkspacePanel({
  history,
  comparison,
  comparisonIds,
  selectedId,
  comparisonIsStale,
  comparisonErrorMessage,
  lastLiveEventAt,
  isLoading,
  isError,
  isComparing,
  isReplaying,
  isDeletingBacktest,
  tableStateControls,
  rangeFilters,
  onRangeFilterChange,
  onCompareSelected,
  onClearComparison,
  onToggleComparison,
  onSelectRun,
  onViewDetails,
  onReplayBacktest,
  onDeleteResult,
}: BacktestHistoryWorkspacePanelProps) {
  const columns = useMemo<ColumnDef<BacktestHistoryItem>[]>(
    () => [
      {
        id: 'compare',
        header: 'Compare',
        enableSorting: false,
        enableColumnFilter: false,
        size: 92,
        minSize: 92,
        meta: {
          filterVariant: 'none',
          headerDescription:
            'Select two or more runs, keep the selection across pages, and compare them side by side.',
        },
        cell: ({ row }) => (
          <Box onClick={(event) => event.stopPropagation()}>
            <Checkbox
              checked={comparisonIds.includes(row.original.id)}
              onChange={() => onToggleComparison(row.original.id)}
              inputProps={{
                'aria-label': `Select backtest ${row.original.id} for comparison`,
              }}
            />
          </Box>
        ),
      },
      {
        accessorKey: 'id',
        header: 'ID',
        enableColumnFilter: false,
        size: 84,
        minSize: 84,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Unique run identifier. Use quick search to jump to a specific run.',
        },
        cell: ({ row }) => <NumericText variant="body2">{row.original.id}</NumericText>,
      },
      {
        accessorKey: 'strategyId',
        header: 'Algorithm',
        size: 220,
        minSize: 180,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Algorithm',
          headerDescription: 'Strategy logic used for the run.',
        },
        cell: ({ row }) => (
          <Stack spacing={0.35}>
            <Typography variant="body2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
              {row.original.strategyId}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Finished {formatDateTime(row.original.timestamp)}
            </Typography>
          </Stack>
        ),
      },
      {
        accessorKey: 'datasetName',
        header: 'Dataset',
        size: 210,
        minSize: 180,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Dataset',
          headerDescription: 'Historical input file used for the run.',
        },
        cell: ({ row }) => (
          <Stack spacing={0.35}>
            <Typography variant="body2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
              {row.original.datasetName ?? 'No dataset label'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Started {formatDateTime(row.original.startedAt ?? row.original.timestamp)}
            </Typography>
          </Stack>
        ),
      },
      {
        accessorKey: 'experimentName',
        header: 'Experiment',
        size: 280,
        minSize: 220,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Experiment',
          headerDescription: 'Repeatable research label for related runs.',
        },
        cell: ({ row }) => (
          <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
            {row.original.experimentName}
          </Typography>
        ),
      },
      {
        id: 'market',
        header: 'Market',
        accessorFn: (row) => `${row.symbol} ${row.timeframe}`,
        size: 196,
        minSize: 164,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Market',
          headerDescription: 'Symbol and timeframe tested in the run.',
        },
        cell: ({ row }) => (
          <Stack spacing={0.35}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {formatBacktestMarketLabel(row.original.symbol)} ({row.original.timeframe})
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.original.currentDataTimestamp
                ? `Current data ${formatProgressTimestamp(row.original.currentDataTimestamp)}`
                : 'Current data waiting for first candle'}
            </Typography>
          </Stack>
        ),
      },
      {
        accessorKey: 'executionStatus',
        header: 'Status',
        size: 240,
        minSize: 220,
        meta: {
          filterVariant: 'select',
          filterOptions: [
            { label: 'Pending', value: 'PENDING' },
            { label: 'Running', value: 'RUNNING' },
            { label: 'Completed', value: 'COMPLETED' },
            { label: 'Failed', value: 'FAILED' },
          ],
          headerDescription: 'Execution state, progress, and last backend telemetry.',
        },
        cell: ({ row }) => {
          const item = row.original;
          const progress = executionProgressValue(item);

          return (
            <Tooltip title={executionStageDescription(item)} arrow placement="top-start">
              <Stack spacing={0.6}>
                <StatusPill
                  label={`${asyncStateLabel(item)} ${progress}%`}
                  tone={executionStatusColor(item.executionStatus)}
                  variant="filled"
                />
                <LinearProgress
                  variant="determinate"
                  value={progress}
                  color={item.executionStatus === 'FAILED' ? 'error' : 'primary'}
                  sx={{ height: 6, borderRadius: 999 }}
                />
                <Typography variant="caption" color="text.secondary">
                  {executionStageLabel(item.executionStage)} | {formatNumber(item.processedCandles)} /{' '}
                  {formatNumber(item.totalCandles)} candles
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Backend {formatLastUpdate(item.lastProgressAt)} | Live {formatLiveEventTimestamp(lastLiveEventAt)}
                </Typography>
              </Stack>
            </Tooltip>
          );
        },
      },
      {
        accessorKey: 'validationStatus',
        header: 'Validation',
        size: 152,
        minSize: 140,
        meta: {
          filterVariant: 'select',
          filterOptions: [
            { label: 'Pending', value: 'PENDING' },
            { label: 'Passed', value: 'PASSED' },
            { label: 'Failed', value: 'FAILED' },
            { label: 'Production ready', value: 'PRODUCTION_READY' },
          ],
          headerDescription: 'Research quality gate result. This is not proof of profitability.',
        },
        cell: ({ row }) => (
          <StatusPill
            label={row.original.validationStatus}
            tone={validationTone(row.original.validationStatus)}
            variant="filled"
          />
        ),
      },
      {
        accessorKey: 'feesBps',
        header: 'Fees',
        size: 110,
        minSize: 96,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Trading fee assumption in basis points.',
        },
        cell: ({ row }) => <NumericText variant="body2">{row.original.feesBps} bps</NumericText>,
      },
      {
        accessorKey: 'slippageBps',
        header: 'Slippage',
        size: 126,
        minSize: 108,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Slippage assumption in basis points.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2">{row.original.slippageBps} bps</NumericText>
        ),
      },
      {
        id: 'actions',
        header: 'Actions',
        enableSorting: false,
        enableColumnFilter: false,
        size: 218,
        minSize: 206,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Open details, replay the setup, or remove a finished run.',
        },
        cell: ({ row }) => {
          const item = row.original;
          return (
            <Stack
              direction={{ xs: 'column', xl: 'row' }}
              spacing={0.75}
              onClick={(event) => event.stopPropagation()}
            >
              <Button
                size="small"
                startIcon={<VisibilityOutlinedIcon />}
                onClick={() => onViewDetails(item.id)}
              >
                Details
              </Button>
              <Button
                size="small"
                startIcon={<ReplayIcon />}
                onClick={() => void onReplayBacktest(item.id)}
                disabled={isReplaying}
              >
                Replay
              </Button>
              <Button
                size="small"
                color="error"
                startIcon={<DeleteOutlineIcon />}
                onClick={() => void onDeleteResult(item)}
                disabled={isDeletingBacktest || isExecutionActive(item)}
              >
                Delete
              </Button>
            </Stack>
          );
        },
      },
    ],
    [
      comparisonIds,
      isDeletingBacktest,
      isReplaying,
      lastLiveEventAt,
      onDeleteResult,
      onReplayBacktest,
      onToggleComparison,
      onViewDetails,
    ]
  );

  const filterSummaryPills = (
    <>
      <StatusPill
        label={`Selected ${comparisonIds.length}`}
        tone={comparisonIds.length >= 2 ? 'success' : 'default'}
      />
      {comparisonIsStale ? (
        <StatusPill label="Comparison needs refresh" tone="warning" variant="filled" />
      ) : null}
      {comparisonErrorMessage ? (
        <StatusPill label="Comparison error" tone="error" variant="filled" />
      ) : null}
    </>
  );

  return (
    <Stack spacing={2}>
      <InteractiveTable
        title="Backtest History"
        description="History now behaves like a working grid: compact rows, sticky headers, persisted widths, sortable columns, and inline filters without hiding the detailed review path."
        data={history.items}
        columns={columns}
        stateControls={tableStateControls}
        manualFiltering
        manualSorting
        manualPagination
        totalRows={history.total}
        loading={isLoading}
        error={isError ? 'Unable to load backtest history.' : null}
        loadingLabel="Loading backtest history..."
        emptyTitle="No backtests match this filter set"
        emptyDescription="Broaden the filters, clear a range, or launch a new run to repopulate the history grid."
        globalFilterPlaceholder="Run ID, strategy, dataset, experiment, or market"
        actions={
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button
              variant="contained"
              onClick={() => void onCompareSelected()}
              disabled={comparisonIds.length < 2 || isComparing}
            >
              Compare selected ({comparisonIds.length})
            </Button>
            <Button variant="outlined" onClick={onClearComparison} disabled={comparisonIds.length === 0}>
              Clear selection
            </Button>
          </Stack>
        }
        stats={
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <StatusPill
              label={`${history.total} runs`}
              tone={history.total > 0 ? 'info' : 'default'}
              variant="filled"
            />
            <StatusPill
              label={selectedId ? `Focused #${selectedId}` : 'No run focused'}
              tone={selectedId ? 'info' : 'default'}
            />
            <StatusPill
              label={isComparing ? 'Refreshing comparison' : 'Comparison idle'}
              tone={isComparing ? 'warning' : 'default'}
            />
          </Stack>
        }
        toolbarFilters={
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField
              size="small"
              label="Fees min"
              type="number"
              value={rangeFilters.feesBpsMin}
              onChange={(event) => onRangeFilterChange('feesBpsMin', event.target.value)}
              inputProps={{ min: 0, step: 1 }}
            />
            <TextField
              size="small"
              label="Fees max"
              type="number"
              value={rangeFilters.feesBpsMax}
              onChange={(event) => onRangeFilterChange('feesBpsMax', event.target.value)}
              inputProps={{ min: 0, step: 1 }}
            />
            <TextField
              size="small"
              label="Slippage min"
              type="number"
              value={rangeFilters.slippageBpsMin}
              onChange={(event) => onRangeFilterChange('slippageBpsMin', event.target.value)}
              inputProps={{ min: 0, step: 1 }}
            />
            <TextField
              size="small"
              label="Slippage max"
              type="number"
              value={rangeFilters.slippageBpsMax}
              onChange={(event) => onRangeFilterChange('slippageBpsMax', event.target.value)}
              inputProps={{ min: 0, step: 1 }}
            />
          </Stack>
        }
        activeFilterPills={filterSummaryPills}
        onRowClick={(row) => onSelectRun(row.id)}
        isRowSelected={(row) => row.id === selectedId}
        getRowId={(row) => String(row.id)}
        maxHeight={720}
      />

      {comparison ? <BacktestComparisonPanel comparison={comparison} /> : null}
    </Stack>
  );
}
