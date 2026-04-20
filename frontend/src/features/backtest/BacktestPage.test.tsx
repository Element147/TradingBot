import { configureStore } from '@reduxjs/toolkit';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import environmentReducer from '../environment/environmentSlice';
import websocketReducer from '../websocket/websocketSlice';

import BacktestPage from './BacktestPage';

const historyItems = [
  {
    id: 42,
    strategyId: 'BOLLINGER_BANDS',
    datasetName: 'BTC 1h 2025',
    experimentName: 'BTC Mean Reversion Retest',
    symbol: 'BTC/USDT',
    timeframe: '1h',
    executionStatus: 'COMPLETED',
    validationStatus: 'PASSED',
    feesBps: 10,
    slippageBps: 3,
    timestamp: '2026-03-10T10:00:00',
    initialBalance: 1000,
    finalBalance: 1080,
    executionStage: 'COMPLETED',
    progressPercent: 100,
    processedCandles: 8760,
    totalCandles: 8760,
    currentDataTimestamp: '2025-12-31T00:00:00',
    statusMessage: 'Backtest completed. Metrics and trade series are ready to review.',
    lastProgressAt: '2026-03-10T10:00:00',
    startedAt: '2026-03-10T09:58:00',
    completedAt: '2026-03-10T10:00:00',
  },
  {
    id: 7,
    strategyId: 'SMA_CROSSOVER',
    datasetName: 'ETH 4h 2024',
    experimentName: 'ETH Trend Validation',
    symbol: 'ETH/USDT',
    timeframe: '4h',
    executionStatus: 'COMPLETED',
    validationStatus: 'FAILED',
    feesBps: 8,
    slippageBps: 2,
    timestamp: '2026-03-05T10:00:00',
    initialBalance: 1000,
    finalBalance: 980,
    executionStage: 'COMPLETED',
    progressPercent: 100,
    processedCandles: 4000,
    totalCandles: 4000,
    currentDataTimestamp: '2024-12-31T00:00:00',
    statusMessage: 'Validation failed after completion.',
    lastProgressAt: '2026-03-05T10:00:00',
    startedAt: '2026-03-05T09:50:00',
    completedAt: '2026-03-05T10:00:00',
  },
] as const;

const datasetItems = [
  {
    id: 7,
    name: 'BTC 1h 2025',
    originalFilename: 'btc.csv',
    rowCount: 100,
    symbolsCsv: 'BTC/USDT',
    dataStart: '2025-01-01T00:00:00',
    dataEnd: '2025-01-05T00:00:00',
    uploadedAt: '2026-03-10T10:00:00',
    checksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    schemaVersion: 'ohlcv-v1',
    archived: false,
    archivedAt: null,
    archiveReason: null,
    usageCount: 1,
    lastUsedAt: '2026-03-10T10:00:00',
    usedByBacktests: true,
    duplicateCount: 1,
    retentionStatus: 'ACTIVE',
  },
  {
    id: 8,
    name: 'ETH 4h 2024',
    originalFilename: 'eth.csv',
    rowCount: 250,
    symbolsCsv: 'ETH/USDT',
    dataStart: '2024-01-01T00:00:00',
    dataEnd: '2024-12-31T00:00:00',
    uploadedAt: '2026-03-01T08:00:00',
    checksumSha256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    schemaVersion: 'ohlcv-v2',
    archived: true,
    archivedAt: '2026-03-12T10:00:00',
    archiveReason: 'Archived after lifecycle review.',
    usageCount: 3,
    lastUsedAt: '2026-03-09T10:00:00',
    usedByBacktests: true,
    duplicateCount: 2,
    retentionStatus: 'ARCHIVED',
  },
] as const;

const detailById = {
  42: {
    ...historyItems[0],
    datasetId: 7,
    experimentKey: 'btc-mean-reversion-retest',
    sharpeRatio: 1.2,
    profitFactor: 1.6,
    winRate: 52,
    maxDrawdown: 18,
    totalTrades: 80,
    startDate: '2025-01-01T00:00:00',
    endDate: '2025-12-31T00:00:00',
    datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    datasetSchemaVersion: 'ohlcv-v1',
    datasetUploadedAt: '2026-03-10T10:00:00',
    datasetArchived: false,
    errorMessage: null,
    availableTelemetrySymbols: ['BTC/USDT'],
  },
  7: {
    ...historyItems[1],
    datasetId: 8,
    experimentKey: 'eth-trend-validation',
    sharpeRatio: 0.7,
    profitFactor: 0.9,
    winRate: 45,
    maxDrawdown: 22,
    totalTrades: 34,
    startDate: '2024-01-01T00:00:00',
    endDate: '2024-12-31T00:00:00',
    datasetChecksumSha256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    datasetSchemaVersion: 'ohlcv-v2',
    datasetUploadedAt: '2026-03-01T08:00:00',
    datasetArchived: true,
    errorMessage: null,
    availableTelemetrySymbols: ['ETH/USDT'],
  },
} as const;

vi.mock('./backtestApi', () => ({
  useGetBacktestAlgorithmsQuery: () => ({
    data: [
      { id: 'BOLLINGER_BANDS', label: 'Bollinger Bands', description: '...', selectionMode: 'SINGLE_SYMBOL' },
      { id: 'SMA_CROSSOVER', label: 'SMA Crossover', description: '...', selectionMode: 'SINGLE_SYMBOL' },
    ],
  }),
  useGetBacktestDatasetsQuery: () => ({
    data: datasetItems,
  }),
  useGetBacktestDatasetRetentionReportQuery: () => ({
    data: {
      totalDatasets: 2,
      activeDatasets: 1,
      archivedDatasets: 1,
      archiveCandidateDatasets: 1,
      duplicateDatasetCount: 1,
      referencedDatasetCount: 2,
      oldestActiveUploadedAt: '2026-03-01T08:00:00',
      newestUploadedAt: '2026-03-10T10:00:00',
    },
  }),
  useGetBacktestExperimentSummariesQuery: () => ({
    data: [
      {
        experimentKey: 'btc-mean-reversion-retest',
        experimentName: 'BTC Mean Reversion Retest',
        latestBacktestId: 42,
        strategyId: 'BOLLINGER_BANDS',
        datasetName: 'BTC 1h 2025',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        latestExecutionStatus: 'COMPLETED',
        latestValidationStatus: 'PASSED',
        runCount: 3,
        latestRunAt: '2026-03-10T10:00:00',
        averageReturnPercent: 6.5,
        bestFinalBalance: 1120,
        worstMaxDrawdown: 18,
      },
    ],
  }),
  useGetBacktestsQuery: () => ({
    data: {
      items: historyItems,
      total: historyItems.length,
      page: 1,
      pageSize: 25,
    },
    isLoading: false,
    isError: false,
  }),
  useGetBacktestDetailsQuery: (id: number) => ({
    data: detailById[id as 7 | 42],
    refetch: vi.fn(),
  }),
  useGetBacktestSummaryQuery: () => ({
    data: undefined,
  }),
  useUploadBacktestDatasetMutation: () => [vi.fn(), { isLoading: false }],
  useArchiveBacktestDatasetMutation: () => [vi.fn(), { isLoading: false }],
  useRestoreBacktestDatasetMutation: () => [vi.fn(), { isLoading: false }],
  useRunBacktestMutation: () => [vi.fn(), { isLoading: false }],
  useReplayBacktestMutation: () => [vi.fn(), { isLoading: false }],
  useDeleteBacktestMutation: () => [vi.fn(), { isLoading: false }],
  useLazyCompareBacktestsQuery: () => [vi.fn(), { data: undefined, isFetching: false, error: undefined }],
}));

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('./BacktestResults', () => ({
  BacktestResults: ({
    details,
  }: {
    details: {
      id: number;
      experimentName: string;
      strategyId: string;
      datasetName: string;
      validationStatus: string;
    };
  }) => (
    <div>
      <div>{`Backtest Details #${details.id}`}</div>
      <div>{`Experiment: ${details.experimentName}`}</div>
      <div>{`Algorithm: ${details.strategyId}`}</div>
      <div>{`Dataset: ${details.datasetName}`}</div>
      <div>{`Validation: ${details.validationStatus}`}</div>
    </div>
  ),
}));

vi.mock('./BacktestComparisonPanel', () => ({
  BacktestComparisonPanel: () => <div>comparison panel</div>,
}));

vi.mock('@/services/axiosClient', () => ({
  default: {
    get: vi.fn(),
  },
  getErrorMessage: () => 'error',
}));

describe('BacktestPage', { timeout: 25000 }, () => {
  const renderPage = () => {
    const store = configureStore({
      reducer: {
        environment: environmentReducer,
        websocket: websocketReducer,
      },
      preloadedState: {
        environment: {
          mode: 'test',
          connectedExchange: null,
          lastSyncTime: null,
        },
        websocket: {
          connected: true,
          connecting: false,
          error: null,
          lastReconnectAttempt: null,
          reconnectAttempts: 0,
          subscribedChannels: ['test.backtests'],
          lastEventTime: '2026-03-10T10:00:00',
          lastEventByType: {
            'backtest.progress': '2026-03-10T10:00:00',
          },
        },
      },
    });

    return render(
      <MemoryRouter>
        <Provider store={store}>
          <BacktestPage />
        </Provider>
      </MemoryRouter>
    );
  };

  it('defaults to review and switches between the new top-level tabs', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Backtest Details #42')).toBeInTheDocument();
    expect(screen.queryByText('Backtest History')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Runs' }));
    expect(await screen.findByText('Run Backtest')).toBeInTheDocument();
    expect(screen.getByText('Experiment Summaries')).toBeInTheDocument();
    expect(screen.queryByText('Backtest Details #42')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Datasets' }));
    expect(await screen.findByText('Dataset inventory')).toBeInTheDocument();
    expect(screen.getAllByText('Dataset lifecycle').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('tab', { name: 'History' }));
    expect(await screen.findByText('Backtest History')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Compare selected/i })).toBeInTheDocument();
  });

  it('sorts the dataset inventory client-side', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('tab', { name: 'Datasets' }));

    const datasetTable = screen.getByRole('table');
    let bodyRows = datasetTable.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('BTC 1h 2025');

    await user.click(screen.getByRole('button', { name: /Name/i }));

    bodyRows = datasetTable.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('BTC 1h 2025');

    await user.click(screen.getByRole('button', { name: /Name/i }));

    bodyRows = datasetTable.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('ETH 4h 2024');
  });

  it('sorts history rows and keeps review separate from history', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('tab', { name: 'History' }));

    const historyTable = screen.getByRole('table');
    let bodyRows = historyTable.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('42');

    await user.click(screen.getByRole('button', { name: /^ID$/i }));

    bodyRows = historyTable.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('7');

    expect(within(bodyRows[0] as HTMLTableRowElement).getByRole('button', { name: 'Details' })).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Review' }));

    expect(await screen.findByText('Backtest Details #42')).toBeInTheDocument();
    expect(screen.queryByText('Backtest History')).not.toBeInTheDocument();
  });
});
