import FileDownloadIcon from '@mui/icons-material/FileDownload';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Button,
  MenuItem,
  Pagination,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import type { Dispatch, SetStateAction } from 'react';

import type { TradeHistoryItem } from './tradesApi';
import type { TradeFilterDraft } from './tradesPageState';
import type { TradeSortField, TradeStats } from './tradeUtils';
import { VirtualizedTradeTable } from './VirtualizedTradeTable';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import {
  EmptyState,
  StatusPill,
  SurfacePanel,
} from '@/components/ui/Workbench';

interface TradesFiltersPanelProps {
  draft: TradeFilterDraft;
  symbols: string[];
  validationError: string | null;
  isFetching: boolean;
  onDraftChange: Dispatch<SetStateAction<TradeFilterDraft>>;
  onSearchIdChange: (value: string) => void;
  onApply: () => void;
  onReset: () => void;
  onRefresh: () => void;
}

export function TradesFiltersPanel({
  draft,
  symbols,
  validationError,
  isFetching,
  onDraftChange,
  onSearchIdChange,
  onApply,
  onReset,
  onRefresh,
}: TradesFiltersPanelProps) {
  return (
    <SurfacePanel
      title="Filter workspace"
      description="Narrow the trade set without losing account, symbol, or time-window context."
      actions={
        <StatusPill
          label={isFetching ? 'Refreshing history' : 'History ready'}
          tone={isFetching ? 'warning' : 'success'}
          variant="filled"
        />
      }
    >
      <Stack spacing={2}>
        <FieldTooltip title="Optional account scope. Leaving empty queries all visible accounts.">
          <TextField
            label="Account ID"
            value={draft.accountId}
            onChange={(event) =>
              onDraftChange((prev) => ({ ...prev, accountId: event.target.value }))
            }
            placeholder="e.g. 1"
            helperText="Optional: load only trades from one account."
          />
        </FieldTooltip>
        <FieldTooltip title="Optional symbol filter. Narrowing too much can hide correlated behavior in review.">
          <TextField
            select
            label="Symbol"
            value={draft.symbol}
            onChange={(event) =>
              onDraftChange((prev) => ({ ...prev, symbol: event.target.value }))
            }
            helperText="Optional: narrow history to one market pair."
          >
            <MenuItem value="">All symbols</MenuItem>
            {symbols.map((symbol) => (
              <MenuItem key={symbol} value={symbol}>
                {symbol}
              </MenuItem>
            ))}
          </TextField>
        </FieldTooltip>
        <FieldTooltip title="Lower date boundary for trade inclusion. Wrong timezone assumptions can shift results.">
          <TextField
            label="Start Date"
            type="datetime-local"
            value={draft.startDate}
            onChange={(event) =>
              onDraftChange((prev) => ({ ...prev, startDate: event.target.value }))
            }
            helperText="Optional: include trades opened after this date and time."
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </FieldTooltip>
        <FieldTooltip title="Upper date boundary for trade inclusion. End before start returns empty or invalid slices.">
          <TextField
            label="End Date"
            type="datetime-local"
            value={draft.endDate}
            onChange={(event) =>
              onDraftChange((prev) => ({ ...prev, endDate: event.target.value }))
            }
            helperText="Optional: include trades opened before this date and time."
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </FieldTooltip>
        <FieldTooltip title="Backend fetch size. Very high limits increase payload size and response time.">
          <TextField
            label="Result Limit"
            type="number"
            value={draft.limit}
            onChange={(event) =>
              onDraftChange((prev) => ({ ...prev, limit: event.target.value }))
            }
            helperText="Number of rows to request from backend (1-1000)."
            inputProps={{ min: 1, max: 1000, step: 1 }}
          />
        </FieldTooltip>
        <FieldTooltip title="Client-side trade ID lookup. Non-numeric values are rejected by validation.">
          <TextField
            label="Search Trade ID"
            value={draft.searchId}
            onChange={(event) => onSearchIdChange(event.target.value)}
            helperText="Debounced by 300ms."
          />
        </FieldTooltip>

        {validationError ? <Alert severity="error">{validationError}</Alert> : null}

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <Button
            variant="contained"
            onClick={onApply}
            disabled={isFetching || Boolean(validationError)}
          >
            Apply filters
          </Button>
          <Button variant="outlined" onClick={onReset} disabled={isFetching}>
            Reset
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={onRefresh}
            disabled={isFetching}
          >
            Refresh
          </Button>
        </Stack>
      </Stack>
    </SurfacePanel>
  );
}

interface TradesResultsPanelProps {
  stats: TradeStats;
  filteredTrades: TradeHistoryItem[];
  pagedTrades: TradeHistoryItem[];
  selectedTradeId: number | null;
  sortField: TradeSortField;
  sortDirection: 'asc' | 'desc';
  page: number;
  totalPages: number;
  timezone: string;
  isError: boolean;
  isFetching: boolean;
  onSortChange: (field: TradeSortField) => void;
  onRowSelect: (trade: TradeHistoryItem) => void;
  onPageChange: (page: number) => void;
  onExport: () => void;
  formatAmount: (amount: number) => string;
}

export function TradesResultsPanel({
  stats,
  filteredTrades,
  pagedTrades,
  selectedTradeId,
  sortField,
  sortDirection,
  page,
  totalPages,
  timezone,
  isError,
  isFetching,
  onSortChange,
  onRowSelect,
  onPageChange,
  onExport,
  formatAmount,
}: TradesResultsPanelProps) {
  return (
    <SurfacePanel
      title="Trade review"
      description="Select a row to open detailed execution evidence while keeping filters and pagination stable."
      actions={
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <StatusPill
            label={selectedTradeId ? `Selected #${selectedTradeId}` : 'No trade selected'}
            tone={selectedTradeId ? 'info' : 'default'}
          />
          <Button variant="outlined" startIcon={<FileDownloadIcon />} onClick={onExport}>
            Export CSV
          </Button>
        </Stack>
      }
    >
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <StatusPill label={`${stats.totalTrades} trades`} tone="info" />
        <StatusPill
          label={`Win rate ${stats.winRate.toFixed(1)}%`}
          tone={stats.winRate >= 50 ? 'success' : 'warning'}
        />
        <StatusPill label={`Profit factor ${stats.profitFactor.toFixed(2)}`} />
        <StatusPill label={`PnL ${formatAmount(stats.totalPnl)}`} tone={stats.totalPnl >= 0 ? 'success' : 'error'} />
        <StatusPill label={`Fees ${formatAmount(stats.totalFees)}`} />
        <StatusPill label={`Slippage ${formatAmount(stats.totalSlippage)}`} />
      </Stack>

      <Typography variant="body2" color="text.secondary">
        Avg win {formatAmount(stats.averageWin)} | Avg loss {formatAmount(stats.averageLoss)} |
        Timezone {timezone}
      </Typography>

      {isError ? <Alert severity="error">Failed to load trade history from backend.</Alert> : null}
      {!isError && isFetching ? <Typography>Loading trade history...</Typography> : null}

      {!isFetching && !isError && filteredTrades.length === 0 ? (
        <EmptyState
          title="No trades found for this filter set"
          description="Widen the time window, clear the account or symbol filter, or refresh to pull a newer slice."
          tone="info"
        />
      ) : null}

      {filteredTrades.length > 0 ? (
        <>
          <VirtualizedTradeTable
            rows={pagedTrades}
            selectedTradeId={selectedTradeId}
            sortField={sortField}
            sortDirection={sortDirection}
            onSortChange={onSortChange}
            onRowSelect={onRowSelect}
          />

          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={1.5}
          >
            <Typography variant="body2" color="text.secondary">
              Page {page} of {totalPages} | Timezone: {timezone}
            </Typography>
            <Pagination
              count={totalPages}
              page={page}
              onChange={(_, nextPage) => onPageChange(nextPage)}
              color="primary"
            />
          </Stack>
        </>
      ) : null}
    </SurfacePanel>
  );
}
