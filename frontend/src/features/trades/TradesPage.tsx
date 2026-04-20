import { Alert, Box, Button, Chip, Grid } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useSelector } from 'react-redux';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

import { TradeDetailsModal } from './TradeDetailsModal';
import { useGetTradeHistoryQuery, type TradeHistoryQuery } from './tradesApi';
import {
  defaultTradeFilterDraft,
  persistTradeFilterDraft,
  readStoredTradeFilterDraft,
  toDateTimeParam,
  type TradeFilterDraft,
} from './tradesPageState';
import { TradesFiltersPanel, TradesResultsPanel } from './TradesPanels';
import {
  buildTradesCsv,
  calculateTradeStats,
  sortTrades,
  type TradeSortField,
} from './tradeUtils';

import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
} from '@/components/layout/PageContent';
import { selectCurrency, selectTimezone } from '@/features/settings/settingsSlice';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';

const tradeSortFields: TradeSortField[] = [
  'id',
  'pair',
  'positionSide',
  'signal',
  'entryTime',
  'exitTime',
  'entryPrice',
  'exitPrice',
  'positionSize',
  'pnl',
  'feesActual',
  'slippageActual',
];

const parsePositiveIntParam = (value: string | null, fallback: number) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

const parseTradeSortFieldParam = (value: string | null): TradeSortField =>
  tradeSortFields.find((field) => field === value) ?? 'entryTime';

export default function TradesPage() {
  const timezone = useSelector(selectTimezone);
  const currency = useSelector(selectCurrency);
  const [searchParams, setSearchParams] = useSearchParams();

  const [draft, setDraft] = useState<TradeFilterDraft>(() => {
    const fromStorage = readStoredTradeFilterDraft();
    if (Array.from(searchParams.keys()).length === 0) {
      return fromStorage;
    }
    return {
      accountId: searchParams.get('accountId') ?? fromStorage.accountId,
      symbol: searchParams.get('symbol') ?? fromStorage.symbol,
      startDate: searchParams.get('startDate') ?? fromStorage.startDate,
      endDate: searchParams.get('endDate') ?? fromStorage.endDate,
      limit: searchParams.get('limit') ?? fromStorage.limit,
      searchId: searchParams.get('searchId') ?? fromStorage.searchId,
    };
  });
  const [query, setQuery] = useState<TradeHistoryQuery>({ limit: 200 });
  const [selectedTradeId, setSelectedTradeId] = useState<number | null>(() => {
    const parsed = Number(searchParams.get('trade'));
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  });
  const [feedback, setFeedback] = useState<{
    severity: 'success' | 'error';
    message: string;
  } | null>(null);
  const [page, setPage] = useState(() => parsePositiveIntParam(searchParams.get('page'), 1));
  const [sortField, setSortField] = useState<TradeSortField>(() =>
    parseTradeSortFieldParam(searchParams.get('sort'))
  );
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>(() =>
    searchParams.get('direction') === 'asc' ? 'asc' : 'desc'
  );

  const debouncedSearchId = useDebouncedValue(draft.searchId, 300);

  const { data, isFetching, isError, refetch } = useGetTradeHistoryQuery(query, {
    pollingInterval: 10000,
    skipPollingIfUnfocused: true,
  });

  const trades = useMemo(() => data?.items ?? [], [data]);
  const limit = Number(draft.limit) || 200;
  const pageSize = 50;

  const validationError = useMemo(() => {
    const parsedLimit = Number(draft.limit);

    if (!Number.isInteger(parsedLimit) || parsedLimit < 1 || parsedLimit > 1000) {
      return 'Limit must be an integer between 1 and 1000.';
    }

    if (draft.accountId.trim()) {
      const accountId = Number(draft.accountId);
      if (!Number.isInteger(accountId) || accountId <= 0) {
        return 'Account ID must be a positive integer.';
      }
    }

    if (draft.searchId.trim()) {
      const tradeId = Number(draft.searchId);
      if (!Number.isInteger(tradeId) || tradeId <= 0) {
        return 'Trade ID search must be a positive integer.';
      }
    }

    if (draft.startDate && draft.endDate && new Date(draft.startDate) > new Date(draft.endDate)) {
      return 'Start date must be earlier than end date.';
    }

    return null;
  }, [draft]);

  const filteredTrades = useMemo(() => {
    const sorted = sortTrades(trades, sortField, sortDirection);

    if (!debouncedSearchId.trim()) {
      return sorted;
    }

    const searchId = Number(debouncedSearchId);
    if (!Number.isInteger(searchId)) {
      return sorted;
    }

    return sorted.filter((trade) => trade.id === searchId);
  }, [debouncedSearchId, sortDirection, sortField, trades]);

  const totalPages = Math.max(1, Math.ceil(filteredTrades.length / pageSize));
  const pagedTrades = useMemo(() => {
    const start = (page - 1) * pageSize;
    return filteredTrades.slice(start, start + pageSize);
  }, [filteredTrades, page]);

  const symbols = useMemo(() => {
    const unique = new Set(trades.map((trade) => trade.pair));
    return Array.from(unique).sort((left, right) => left.localeCompare(right));
  }, [trades]);

  const stats = useMemo(() => calculateTradeStats(filteredTrades), [filteredTrades]);
  const summaryItems = useMemo<PageMetricItem[]>(
    () => [
      {
        label: 'Filtered Trades',
        value: stats.totalTrades.toString(),
        detail: `Current page ${page} of ${totalPages}.`,
        tone: stats.totalTrades > 0 ? 'info' : 'default',
      },
      {
        label: 'Win Rate',
        value: `${stats.winRate.toFixed(1)}%`,
        detail: `Profit factor ${stats.profitFactor.toFixed(2)}.`,
        tone: stats.winRate >= 50 ? 'success' : 'warning',
      },
      {
        label: 'Timezone',
        value: timezone,
        detail: 'Trade timestamps use this display timezone.',
        tone: 'default',
      },
      {
        label: 'Currency View',
        value: currency,
        detail: 'Formatting preference only; backend trade data is unchanged.',
        tone: 'default',
      },
    ],
    [currency, page, stats.profitFactor, stats.totalTrades, stats.winRate, timezone, totalPages]
  );

  const formatAmount = (amount: number): string => {
    if (currency === 'BTC') {
      return `${amount.toFixed(8)} BTC`;
    }

    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2,
    }).format(amount);
  };

  useEffect(() => {
    persistTradeFilterDraft(draft);
  }, [draft]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (draft.accountId) params.set('accountId', draft.accountId);
    if (draft.symbol) params.set('symbol', draft.symbol);
    if (draft.startDate) params.set('startDate', draft.startDate);
    if (draft.endDate) params.set('endDate', draft.endDate);
    if (draft.limit) params.set('limit', draft.limit);
    if (draft.searchId) params.set('searchId', draft.searchId);
    if (selectedTradeId) params.set('trade', String(selectedTradeId));
    if (page > 1) params.set('page', String(page));
    if (sortField !== 'entryTime') params.set('sort', sortField);
    if (sortDirection !== 'desc') params.set('direction', sortDirection);
    setSearchParams(params, { replace: true });
  }, [draft, page, selectedTradeId, setSearchParams, sortDirection, sortField]);

  const applyFilters = () => {
    if (validationError) {
      setFeedback({ severity: 'error', message: validationError });
      return;
    }

    const nextQuery: TradeHistoryQuery = {
      limit,
    };

    if (draft.accountId.trim()) {
      nextQuery.accountId = Number(draft.accountId.trim());
    }
    if (draft.symbol.trim()) {
      nextQuery.symbol = draft.symbol.trim();
    }
    if (draft.startDate.trim()) {
      nextQuery.startDate = toDateTimeParam(draft.startDate.trim());
    }
    if (draft.endDate.trim()) {
      nextQuery.endDate = toDateTimeParam(draft.endDate.trim());
    }

    setQuery(nextQuery);
    setPage(1);
    setFeedback({ severity: 'success', message: 'Filters applied.' });
  };

  const resetFilters = () => {
    setDraft(defaultTradeFilterDraft);
    setQuery({ limit: 200 });
    setPage(1);
    setFeedback({ severity: 'success', message: 'Filters reset.' });
  };

  const exportCsv = () => {
    if (filteredTrades.length === 0) {
      setFeedback({ severity: 'error', message: 'There are no rows to export.' });
      return;
    }

    const csv = buildTradesCsv(filteredTrades);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `trades_${new Date()
      .toISOString()
      .replaceAll(':', '')
      .replaceAll('-', '')
      .slice(0, 15)}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    setFeedback({ severity: 'success', message: 'CSV export downloaded.' });
  };

  const changeSort = (field: TradeSortField) => {
    if (field === sortField) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
      setPage(1);
      return;
    }

    setSortField(field);
    setSortDirection('asc');
    setPage(1);
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Review and export"
          description="Use a lighter filter flow to narrow the trade set, inspect fills, and export clean evidence without losing timezone or account context."
          actions={
            <>
              <Button component={RouterLink} to="/paper" variant="contained">
                Open paper desk
              </Button>
              <Button component={RouterLink} to="/strategies" variant="outlined">
                Open strategies
              </Button>
            </>
          }
          chips={
            <>
              <Chip label="Timezone-aware review" variant="outlined" />
              <Chip label="CSV export stays available" variant="outlined" />
              <Chip label="Paper and research history only" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={summaryItems} />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Box sx={{ position: { lg: 'sticky' }, top: { lg: 16 } }}>
              <TradesFiltersPanel
                draft={draft}
                symbols={symbols}
                validationError={validationError}
                isFetching={isFetching}
                onDraftChange={setDraft}
                onSearchIdChange={(value) => {
                  setDraft((prev) => ({ ...prev, searchId: value }));
                  setPage(1);
                }}
                onApply={applyFilters}
                onReset={resetFilters}
                onRefresh={() => void refetch()}
              />
            </Box>
          </Grid>

          <Grid size={{ xs: 12, lg: 8 }}>
            <TradesResultsPanel
              stats={stats}
              filteredTrades={filteredTrades}
              pagedTrades={pagedTrades}
              selectedTradeId={selectedTradeId}
              sortField={sortField}
              sortDirection={sortDirection}
              page={page}
              totalPages={totalPages}
              timezone={timezone}
              isError={isError}
              isFetching={isFetching}
              onSortChange={changeSort}
              onRowSelect={(trade) => setSelectedTradeId(trade.id)}
              onPageChange={setPage}
              onExport={exportCsv}
              formatAmount={formatAmount}
            />
          </Grid>
        </Grid>
      </PageContent>

      <TradeDetailsModal
        tradeId={selectedTradeId}
        accountId={query.accountId}
        open={selectedTradeId !== null}
        onClose={() => setSelectedTradeId(null)}
      />
    </AppLayout>
  );
}
