import { configureStore } from '@reduxjs/toolkit';
import { fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ForwardTestingPage from './ForwardTestingPage';

import authReducer from '@/features/auth/authSlice';
import environmentReducer from '@/features/environment/environmentSlice';
import settingsReducer from '@/features/settings/settingsSlice';
import websocketReducer from '@/features/websocket/websocketSlice';

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/features/strategies/strategiesApi', () => ({
  useGetStrategiesQuery: () => ({
    data: [
      {
        id: 7,
        name: 'BTC Trend Pullback',
        type: 'TREND_PULLBACK_CONTINUATION',
        status: 'RUNNING',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 0.01,
        maxPositionSize: 0.1,
        profitLoss: 152.44,
        tradeCount: 14,
        currentDrawdown: 28.31,
        paperMode: true,
        shortSellingEnabled: false,
        configVersion: 3,
        lastConfigChangedAt: '2026-03-26T08:00:00Z',
      },
    ],
    isLoading: false,
    isError: false,
  }),
  useGetStrategyConfigHistoryQuery: () => ({
    data: [
      {
        id: 1,
        versionNumber: 3,
        changeReason: 'Tightened pullback filter after review.',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 0.01,
        maxPositionSize: 0.1,
        status: 'RUNNING',
        paperMode: true,
        shortSellingEnabled: false,
        changedAt: '2026-03-26T08:00:00Z',
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
          id: 10,
          pair: 'BTC/USDT',
          entryTime: '2026-03-26T09:00:00Z',
          entryPrice: 62500,
          exitTime: '2026-03-26T10:30:00Z',
          exitPrice: 62880,
          signal: 'BUY',
          positionSide: 'LONG',
          positionSize: 0.05,
          riskAmount: 25,
          pnl: 19.5,
          feesActual: 0.8,
          slippageActual: 0.5,
          stopLoss: 61800,
          takeProfit: 63200,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 12,
    },
    isLoading: false,
  }),
}));

vi.mock('@/features/paperApi', () => ({
  useGetPaperTradingStateQuery: () => ({
    data: {
      paperMode: true,
      cashBalance: 10000,
      positionCount: 1,
      totalOrders: 12,
      openOrders: 1,
      filledOrders: 11,
      cancelledOrders: 0,
      lastOrderAt: '2026-03-26T09:10:00Z',
      lastPositionUpdateAt: '2026-03-26T09:12:00Z',
      staleOpenOrderCount: 0,
      stalePositionCount: 0,
      recoveryStatus: 'HEALTHY',
      recoveryMessage: 'Paper desk is synchronized.',
      incidentSummary: 'No active paper incidents.',
      alerts: [
        {
          severity: 'INFO',
          code: 'SYNC_OK',
          summary: 'Forward-test desk synchronized.',
          recommendedAction: 'Continue observation and document any anomalies.',
        },
      ],
    },
  }),
}));

vi.mock('@/features/settings/exchangeApi', () => ({
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
        paperEventCount: 1,
        liveEventCount: 0,
        latestEventAt: '2026-03-26T09:20:00Z',
      },
      events: [
        {
          id: 44,
          actor: 'operator',
          action: 'STRATEGY_REVIEWED',
          environment: 'paper',
          targetType: 'STRATEGY',
          targetId: '7',
          outcome: 'SUCCESS',
          details: 'Reviewed the reclaim signal before keeping the strategy active.',
          createdAt: '2026-03-26T09:20:00Z',
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock('./forwardTestingApi', () => ({
  useGetForwardTestingStatusQuery: () => ({
    data: {
      accountValue: '10240.00',
      pnl: '240.00',
      pnlPercent: '2.40',
      sharpeRatio: '1.34',
      maxDrawdown: '65.00',
      maxDrawdownPercent: '0.63',
      openPositions: 1,
      totalTrades: 12,
      winRate: '58.33',
      profitFactor: '1.61',
      status: 'ACTIVE',
    },
  }),
}));

vi.mock('./ForwardSignalTimelineChart', () => ({
  ForwardSignalTimelineChart: ({ strategyName }: { strategyName: string }) => (
    <div data-testid="signal-chart">{strategyName} signal chart</div>
  ),
}));

vi.mock('@/components/workspace/ActiveAlgorithmExplainabilityPanel', () => ({
  ActiveAlgorithmExplainabilityPanel: ({
    title,
    summary,
    extraSections = [],
  }: {
    title: ReactNode;
    summary?: ReactNode;
    extraSections?: Array<{ id: string; title: ReactNode; content: ReactNode }>;
  }) => (
    <section data-testid="forward-explainability-panel">
      <div>{title}</div>
      {summary}
      {extraSections.map((section) => (
        <div key={section.id}>
          <div>{section.title}</div>
          <div>{section.content}</div>
        </div>
      ))}
    </section>
  ),
}));

vi.mock('@/components/workspace/ExecutionWorkspacePrimitives', () => ({
  ExecutionStatusRail: ({
    title,
    items,
  }: {
    title: ReactNode;
    items: Array<{ label: ReactNode; value: ReactNode; detail?: ReactNode }>;
  }) => (
    <section>
      <div>{title}</div>
      {items.map((item, index) => (
        <div key={`status-${index}`}>
          <span>{item.label}</span>
          <span>{item.value}</span>
          {item.detail ? <span>{item.detail}</span> : null}
        </div>
      ))}
    </section>
  ),
  LiveMetricStrip: ({
    title,
    items,
  }: {
    title: ReactNode;
    items: Array<{ label: ReactNode; value: ReactNode; detail?: ReactNode }>;
  }) => (
    <section>
      <div>{title}</div>
      {items.map((item, index) => (
        <div key={`metric-${index}`}>
          <span>{item.label}</span>
          <span>{item.value}</span>
          {item.detail ? <span>{item.detail}</span> : null}
        </div>
      ))}
    </section>
  ),
  ExecutionCard: ({
    title,
    subtitle,
    detail,
    onSelect,
  }: {
    title: ReactNode;
    subtitle?: ReactNode;
    detail?: ReactNode;
    onSelect?: () => void;
  }) => (
    <button type="button" onClick={onSelect}>
      <span>{title}</span>
      {subtitle ? <span>{subtitle}</span> : null}
      {detail ? <span>{detail}</span> : null}
    </button>
  ),
  InvestigationLogPanel: ({
    title,
    entries,
  }: {
    title: ReactNode;
    entries: Array<{ id: string; title: ReactNode; detail: ReactNode }>;
  }) => (
    <section>
      <div>{title}</div>
      {entries.map((entry) => (
        <div key={entry.id}>
          <div>{entry.title}</div>
          <div>{entry.detail}</div>
        </div>
      ))}
    </section>
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
        mode: 'live' as const,
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
        subscribedChannels: ['test.paper'],
        lastEventTime: '2026-03-26T09:00:00Z',
        lastEventByType: {},
      },
    },
  });

describe('ForwardTestingPage', { timeout: 15000 }, () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('renders the forward-testing workspace with strategy evidence and notes', () => {
    render(
      <Provider store={createStore()}>
        <MemoryRouter>
          <ForwardTestingPage />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Forward-testing posture')).toBeInTheDocument();
    expect(screen.getByText('BTC Trend Pullback')).toBeInTheDocument();
    expect(screen.getByTestId('signal-chart')).toHaveTextContent('BTC Trend Pullback signal chart');
    expect(screen.getByText('Investigation history')).toBeInTheDocument();
    expect(screen.getByText('SYNC_OK: Forward-test desk synchronized.')).toBeInTheDocument();
    expect(screen.getByText('STRATEGY_REVIEWED success')).toBeInTheDocument();
  });

  it('stores an operator note locally for the selected strategy', () => {
    render(
      <Provider store={createStore()}>
        <MemoryRouter>
          <ForwardTestingPage />
        </MemoryRouter>
      </Provider>
    );

    fireEvent.change(screen.getByLabelText('Operator follow-up note'), {
      target: { value: 'Watch the next pullback for weaker reclaim volume.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save local note' }));

    expect(screen.getByText('Operator note saved locally for this workstation.')).toBeInTheDocument();
    expect(screen.getByText('Watch the next pullback for weaker reclaim volume.')).toBeInTheDocument();
    expect(localStorage.getItem('forward-testing-notes')).toContain(
      'Watch the next pullback for weaker reclaim volume.'
    );
  });
});
