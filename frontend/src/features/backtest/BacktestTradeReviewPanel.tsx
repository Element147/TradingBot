import {
  Alert,
  InputAdornment,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { useDeferredValue, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { useGetBacktestTradeSeriesQuery, type BacktestDetails } from './backtestApi';
import { BacktestVirtualizedTradeTable } from './BacktestVirtualizedTradeTable';

import { EmptyState, SurfacePanel } from '@/components/ui/Workbench';
import { formatDateTime } from '@/utils/formatters';

export type TradeSortField =
  | 'symbol'
  | 'side'
  | 'entryTime'
  | 'exitTime'
  | 'quantity'
  | 'entryPrice'
  | 'exitPrice'
  | 'pnlValue'
  | 'returnPct';

export type TradeSortDirection = 'asc' | 'desc';
type TradeSideFilter = 'ALL' | 'LONG' | 'SHORT';
type TradePnlFilter = 'ALL' | 'WINNERS' | 'LOSERS';

export interface TradeReviewRow {
  id: string;
  symbol: string;
  side: 'LONG' | 'SHORT';
  entryTime: string;
  exitTime: string;
  quantity: number;
  entryPrice: number;
  exitPrice: number;
  pnlValue: number;
  returnPct: number;
}

interface BacktestTradeReviewPanelProps {
  details: BacktestDetails;
  selectedSymbol: string;
}

const compareTradeValues = (
  left: string | number,
  right: string | number,
  direction: TradeSortDirection
) => {
  const multiplier = direction === 'asc' ? 1 : -1;

  if (typeof left === 'number' && typeof right === 'number') {
    return (left - right) * multiplier;
  }

  return String(left).localeCompare(String(right)) * multiplier;
};

export default function BacktestTradeReviewPanel({
  details,
  selectedSymbol,
}: BacktestTradeReviewPanelProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [tradeQuery, setTradeQuery] = useState('');
  const [tradeSideFilter, setTradeSideFilter] = useState<TradeSideFilter>('ALL');
  const [tradePnlFilter, setTradePnlFilter] = useState<TradePnlFilter>('ALL');
  const [tradeSortField, setTradeSortField] = useState<TradeSortField>('entryTime');
  const [tradeSortDirection, setTradeSortDirection] = useState<TradeSortDirection>('desc');
  const deferredTradeQuery = useDeferredValue(tradeQuery);

  const {
    data: tradeSeries = [],
    isLoading,
    isFetching,
  } = useGetBacktestTradeSeriesQuery(details.id);

  const tradeRows = useMemo<TradeReviewRow[]>(
    () =>
      tradeSeries
        .filter((trade) => trade.symbol === selectedSymbol)
        .map((trade, index) => ({
          ...trade,
          id: `${trade.symbol}-${trade.entryTime}-${index}`,
          pnlValue: trade.exitValue - trade.entryValue,
        })),
    [selectedSymbol, tradeSeries]
  );

  const filteredTradeRows = useMemo(() => {
    const normalizedQuery = deferredTradeQuery.trim().toLowerCase();

    return tradeRows
      .filter((trade) => {
        if (tradeSideFilter !== 'ALL' && trade.side !== tradeSideFilter) {
          return false;
        }

        if (tradePnlFilter === 'WINNERS' && trade.pnlValue < 0) {
          return false;
        }

        if (tradePnlFilter === 'LOSERS' && trade.pnlValue >= 0) {
          return false;
        }

        if (!normalizedQuery) {
          return true;
        }

        return [
          trade.symbol,
          trade.side,
          formatDateTime(trade.entryTime),
          formatDateTime(trade.exitTime),
        ].some((value) => value.toLowerCase().includes(normalizedQuery));
      })
      .sort((left, right) => {
        switch (tradeSortField) {
          case 'symbol':
          case 'side':
            return compareTradeValues(left[tradeSortField], right[tradeSortField], tradeSortDirection);
          case 'entryTime':
          case 'exitTime':
            return compareTradeValues(
              new Date(left[tradeSortField]).getTime(),
              new Date(right[tradeSortField]).getTime(),
              tradeSortDirection
            );
          default:
            return compareTradeValues(
              left[tradeSortField],
              right[tradeSortField],
              tradeSortDirection
            );
        }
      });
  }, [
    deferredTradeQuery,
    tradePnlFilter,
    tradeRows,
    tradeSideFilter,
    tradeSortDirection,
    tradeSortField,
  ]);

  const selectedTradeId = searchParams.get('trade');

  if (isLoading) {
    return (
      <Alert severity="info">
        Loading the trade review table. Trade rows are requested only when this section is opened.
      </Alert>
    );
  }

  if (isFetching) {
    return <Alert severity="info">Refreshing the trade review table.</Alert>;
  }

  return (
    <SurfacePanel
      title="Trade review table"
      description="Trade review is now isolated from the chart and analytics panes so it only loads when you open it."
      contentSx={{ gap: 1.5 }}
    >
      {filteredTradeRows.length > 0 ? (
        <>
          <Stack
            direction={{ xs: 'column', lg: 'row' }}
            spacing={1}
            alignItems={{ xs: 'stretch', lg: 'center' }}
          >
            <TextField
              size="small"
              label="Filter trades"
              value={tradeQuery}
              onChange={(event) => setTradeQuery(event.target.value)}
              placeholder="Symbol, side, timestamp"
              sx={{ minWidth: { xs: '100%', lg: 280 } }}
              slotProps={{
                input: {
                  startAdornment: <InputAdornment position="start">Find</InputAdornment>,
                },
              }}
            />
            <Select
              size="small"
              value={tradeSideFilter}
              onChange={(event) => setTradeSideFilter(event.target.value)}
              sx={{ minWidth: 140 }}
            >
              <MenuItem value="ALL">All sides</MenuItem>
              <MenuItem value="LONG">Long only</MenuItem>
              <MenuItem value="SHORT">Short only</MenuItem>
            </Select>
            <Select
              size="small"
              value={tradePnlFilter}
              onChange={(event) => setTradePnlFilter(event.target.value)}
              sx={{ minWidth: 150 }}
            >
              <MenuItem value="ALL">All outcomes</MenuItem>
              <MenuItem value="WINNERS">Winners</MenuItem>
              <MenuItem value="LOSERS">Losers</MenuItem>
            </Select>
          </Stack>

          <BacktestVirtualizedTradeTable
            rows={filteredTradeRows}
            selectedTradeId={selectedTradeId}
            sortField={tradeSortField}
            sortDirection={tradeSortDirection}
            onSortChange={(nextField) => {
              if (tradeSortField === nextField) {
                setTradeSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
                return;
              }

              setTradeSortField(nextField);
              setTradeSortDirection(nextField === 'entryTime' || nextField === 'exitTime' ? 'desc' : 'asc');
            }}
            onRowSelect={(trade) => {
              const nextParams = new URLSearchParams(searchParams);
              nextParams.set('trade', trade.id);
              setSearchParams(nextParams, { replace: true });
            }}
          />
        </>
      ) : (
        <EmptyState
          title="No trade windows for this symbol"
          description="Switch the symbol in the workspace or choose another run to inspect a trade table here."
          tone="info"
        />
      )}
    </SurfacePanel>
  );
}
