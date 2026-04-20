import { configureStore } from '@reduxjs/toolkit';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import LiveTradingPage from './LiveTradingPage';

import authReducer from '@/features/auth/authSlice';
import environmentReducer from '@/features/environment/environmentSlice';
import settingsReducer from '@/features/settings/settingsSlice';
import websocketReducer from '@/features/websocket/websocketSlice';

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/features/account/accountApi', () => ({
  useGetBalanceQuery: () => ({
    data: undefined,
    isError: true,
    error: {
      status: 409,
      data: {
        message:
          'Live account reads are unavailable on this backend. Exchange connectivity is verified, but /api/account/* is not wired to live exchange balances, positions, or trade history.',
      },
    },
  }),
  useGetPerformanceQuery: () => ({
    data: undefined,
    isError: true,
    error: {
      status: 409,
      data: {
        message:
          'Live account reads are unavailable on this backend. Exchange connectivity is verified, but /api/account/* is not wired to live exchange balances, positions, or trade history.',
      },
    },
  }),
  useGetOpenPositionsQuery: () => ({
    data: [],
    isError: true,
    error: {
      status: 409,
      data: {
        message:
          'Live account reads are unavailable on this backend. Exchange connectivity is verified, but /api/account/* is not wired to live exchange balances, positions, or trade history.',
      },
    },
  }),
  useGetRecentTradesQuery: () => ({
    data: [],
    isError: true,
    error: {
      status: 409,
      data: {
        message:
          'Live account reads are unavailable on this backend. Exchange connectivity is verified, but /api/account/* is not wired to live exchange balances, positions, or trade history.',
      },
    },
  }),
}));

vi.mock('@/features/settings/exchangeApi', () => ({
  useGetSavedExchangeConnectionsQuery: () => ({
    data: {
      activeConnectionId: 'binance-live',
      connections: [
        {
          id: 'binance-live',
          name: 'Binance Live',
          exchange: 'binance',
          apiKey: '',
          apiSecret: '',
          testnet: false,
          active: true,
        },
      ],
    },
  }),
  useGetExchangeConnectionStatusQuery: () => ({
    data: {
      connected: true,
      exchange: 'binance',
      lastSync: '2026-03-26T09:00:00Z',
      rateLimitUsage: 'used-weight-1m=12',
      error: null,
    },
    isError: false,
  }),
  useGetAuditEventsQuery: () => ({
    data: {
      summary: {
        visibleEventCount: 1,
        totalMatchingEvents: 1,
        successCount: 1,
        failedCount: 0,
        uniqueActors: 1,
        uniqueActions: 1,
        testEventCount: 0,
        paperEventCount: 0,
        liveEventCount: 1,
        latestEventAt: '2026-03-26T09:15:00Z',
      },
      events: [
        {
          id: 17,
          actor: 'operator',
          action: 'LIVE_MONITOR_REVIEWED',
          environment: 'live',
          targetType: 'STRATEGY',
          targetId: '1',
          outcome: 'SUCCESS',
          details: 'Reviewed live signal posture while execution stayed blocked.',
          createdAt: '2026-03-26T09:15:00Z',
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock('@/features/strategies/strategiesApi', () => ({
  useGetStrategiesQuery: () => ({
    data: [
      {
        id: 1,
        name: 'BTC Live Monitor',
        type: 'SMA_CROSSOVER',
        status: 'RUNNING',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.01,
        minPositionSize: 0.01,
        maxPositionSize: 0.05,
        profitLoss: 45.25,
        tradeCount: 6,
        currentDrawdown: 8.1,
        paperMode: false,
        shortSellingEnabled: false,
        configVersion: 4,
        lastConfigChangedAt: '2026-03-26T08:30:00Z',
      },
    ],
    isLoading: false,
    isError: false,
  }),
  useGetStrategyConfigHistoryQuery: () => ({
    data: [
      {
        id: 4,
        versionNumber: 4,
        changeReason: 'Last approved monitoring config.',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.01,
        minPositionSize: 0.01,
        maxPositionSize: 0.05,
        status: 'RUNNING',
        paperMode: false,
        shortSellingEnabled: false,
        changedAt: '2026-03-26T08:30:00Z',
      },
    ],
    isLoading: false,
  }),
}));

vi.mock('@/features/trades/tradesApi', () => ({
  useGetTradeHistoryQuery: () => ({
    data: {
      items: [
        {
          id: 31,
          pair: 'BTC/USDT',
          entryTime: '2026-03-26T09:00:00Z',
          entryPrice: 64000,
          exitTime: null,
          exitPrice: null,
          signal: 'BUY',
          positionSide: 'LONG',
          positionSize: 0.02,
          riskAmount: 12,
          pnl: 4.1,
          feesActual: 0.6,
          slippageActual: 0.4,
          stopLoss: 63200,
          takeProfit: 64850,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 12,
    },
    isLoading: false,
  }),
}));

vi.mock('@/features/forwardTesting/ForwardSignalTimelineChart', () => ({
  ForwardSignalTimelineChart: ({ strategyName }: { strategyName: string }) => (
    <div data-testid="live-signal-chart">{strategyName} live signal chart</div>
  ),
}));

const createStore = () =>
  configureStore({
    reducer: {
      auth: authReducer,
      environment: environmentReducer,
      settings: settingsReducer,
      websocket: websocketReducer,
    },
    preloadedState: {
      auth: {
        token: 'mock-token',
        refreshToken: null,
        user: { id: '1', username: 'tester', email: 'tester@example.com', role: 'trader' },
        isAuthenticated: true,
        loading: false,
        error: null,
        sessionTimeout: null,
        lastActivity: Date.now(),
      },
      environment: {
        mode: 'test' as const,
        connectedExchange: null,
        lastSyncTime: null,
      },
      settings: {
        theme: 'light' as const,
        currency: 'USD',
        timezone: 'UTC',
        textScale: 1,
        notifications: {
          emailAlerts: true,
          telegramAlerts: false,
          profitLossThreshold: 5,
          drawdownThreshold: 15,
          riskThreshold: 75,
        },
      },
      websocket: {
        connected: true,
        connecting: false,
        error: null,
        lastReconnectAttempt: null,
        reconnectAttempts: 0,
        subscribedChannels: ['live.monitor'],
        lastEventTime: '2026-03-26T09:00:00Z',
        lastEventByType: {},
      },
    },
  });

describe('LiveTradingPage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('renders monitor-only live workspace state with capability warning and strategy evidence', () => {
    render(
      <Provider store={createStore()}>
        <MemoryRouter>
          <LiveTradingPage />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Live route posture')).toBeInTheDocument();
    expect(screen.getByText('Live monitoring summary')).toBeInTheDocument();
    expect(screen.getByText('Monitored live strategies')).toBeInTheDocument();
    expect(screen.getByText('BTC Live Monitor')).toBeInTheDocument();
    expect(screen.getByText('Live execution')).toBeInTheDocument();
    expect(screen.getByText('Fail-closed')).toBeInTheDocument();
    expect(screen.getByText(/custom parameter edits, and order entry are not available/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Live account reads are unavailable on this backend\./i).length).toBeGreaterThan(0);
    expect(screen.getByTestId('live-signal-chart')).toHaveTextContent('BTC Live Monitor live signal chart');
    expect(screen.getByText('LIVE_MONITOR_REVIEWED success')).toBeInTheDocument();
  });
});
