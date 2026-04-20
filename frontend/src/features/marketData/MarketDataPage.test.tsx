import { configureStore } from '@reduxjs/toolkit';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import environmentReducer from '../environment/environmentSlice';
import websocketReducer from '../websocket/websocketSlice';

import MarketDataPage from './MarketDataPage';

const createJobMock = vi.fn();
const retryJobMock = vi.fn();
const cancelJobMock = vi.fn();

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('./marketDataApi', () => ({
  useGetMarketDataProvidersQuery: () => ({
    data: [
      {
        id: 'binance',
        label: 'Binance',
        description: 'Public spot klines',
        supportedAssetTypes: ['CRYPTO'],
        supportedTimeframes: ['1m', '1h', '4h', '1d'],
        apiKeyRequired: false,
        apiKeyEnvironmentVariable: null,
        apiKeyConfigured: true,
        apiKeyConfiguredSource: 'NOT_REQUIRED',
        supportsAdjusted: false,
        supportsRegularSessionOnly: false,
        symbolExamples: ['BTC/USDT', 'ETH/USDT'],
        docsUrl: 'https://example.com/docs',
        signupUrl: 'https://example.com/signup',
        accountNotes: 'No API key required.',
      },
    ],
  }),
  useGetMarketDataJobsQuery: () => ({
    data: [
      {
        id: 12,
        providerId: 'binance',
        providerLabel: 'Binance',
        assetType: 'CRYPTO',
        datasetName: 'BTC majors 1h',
        symbolsCsv: 'BTC/USDT,ETH/USDT',
        timeframe: '1h',
        startDate: '2024-03-12',
        endDate: '2026-03-12',
        adjusted: false,
        regularSessionOnly: false,
        status: 'WAITING_RETRY',
        statusMessage: 'Provider asked the downloader to wait.',
        nextRetryAt: '2026-03-12T10:00:00',
        currentSymbolIndex: 0,
        totalSymbols: 2,
        currentSymbol: 'BTC/USDT',
        importedRowCount: 500,
        datasetId: null,
        datasetReady: false,
        currentChunkStart: '2025-01-01T00:00:00',
        attemptCount: 3,
        createdAt: '2026-03-12T09:00:00',
        updatedAt: '2026-03-12T09:05:00',
        startedAt: '2026-03-12T09:00:30',
        completedAt: null,
      },
    ],
  }),
  useCreateMarketDataJobMutation: () => [
    createJobMock,
    { isLoading: false },
  ],
  useRetryMarketDataJobMutation: () => [
    retryJobMock,
    { isLoading: false },
  ],
  useCancelMarketDataJobMutation: () => [
    cancelJobMock,
    { isLoading: false },
  ],
}));

vi.mock('@/services/api', () => ({
  getApiErrorMessage: () => 'failed',
}));

describe('MarketDataPage', { timeout: 15000 }, () => {
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
          subscribedChannels: ['test.marketData'],
          lastEventTime: '2026-03-12T09:05:00',
          lastEventByType: {
            'marketData.import.progress': '2026-03-12T09:05:00',
          },
        },
      },
    });

    return render(
      <Provider store={store}>
        <BrowserRouter>
          <MarketDataPage />
        </BrowserRouter>
      </Provider>
    );
  };

  beforeEach(() => {
    createJobMock.mockReset();
    retryJobMock.mockReset();
    cancelJobMock.mockReset();
    createJobMock.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          id: 99,
          providerLabel: 'Binance',
        }),
    });
    retryJobMock.mockReturnValue({ unwrap: () => Promise.resolve({}) });
    cancelJobMock.mockReturnValue({ unwrap: () => Promise.resolve({}) });
  });

  it('renders provider setup and waiting job information', () => {
    renderPage();

    expect(screen.getByText('Provider-backed imports')).toBeInTheDocument();
    expect(screen.getByText(/Import transport: live WebSocket stream connected/i)).toBeInTheDocument();
    expect(screen.getByText('Step 3. Job telemetry')).toBeInTheDocument();
    expect(screen.getByText(/This provider works without an API key/i)).toBeInTheDocument();
    expect(screen.getByText(/waiting for provider retry windows/i)).toBeInTheDocument();
    expect(screen.getByText(/BTC majors 1h/i)).toBeInTheDocument();
  });

  it('creates a market data job with parsed symbols', async () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('Symbols'), {
      target: { value: 'BTC/USDT\nETH/USDT' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create download job/i }));

    await waitFor(() => {
      expect(createJobMock).toHaveBeenCalledWith({
        providerId: 'binance',
        assetType: 'CRYPTO',
        symbols: ['BTC/USDT', 'ETH/USDT'],
        timeframe: '1h',
        startDate: '2024-03-12',
        endDate: '2026-03-12',
        datasetName: undefined,
        adjusted: false,
        regularSessionOnly: false,
      });
    });
  });
});
