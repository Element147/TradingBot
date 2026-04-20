import { configureStore } from '@reduxjs/toolkit';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';
import { describe, expect, it, vi, beforeEach } from 'vitest';

import environmentReducer from '../environment/environmentSlice';

import PaperTradingPage from './PaperTradingPage';

const placeOrderMock = vi.fn();
const fillOrderMock = vi.fn();
const cancelOrderMock = vi.fn();

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/features/paperApi', () => ({
  useGetPaperTradingStateQuery: () => ({
    data: {
      paperMode: true,
      cashBalance: 10000,
      positionCount: 1,
      totalOrders: 2,
      openOrders: 1,
      filledOrders: 1,
      cancelledOrders: 0,
      lastOrderAt: '2026-03-20T09:00:00',
      lastPositionUpdateAt: '2026-03-20T09:05:00',
      staleOpenOrderCount: 0,
      stalePositionCount: 0,
      recoveryStatus: 'HEALTHY',
      recoveryMessage: 'Paper state looks healthy.',
      incidentSummary: 'No paper incidents detected.',
      alerts: [],
    },
    isLoading: false,
    isError: false,
  }),
  useGetPaperOrdersQuery: () => ({
    data: [
      {
        id: 11,
        symbol: 'BTC/USDT',
        side: 'BUY',
        status: 'NEW',
        quantity: 0.05,
        price: 50000,
        fillPrice: null,
        fees: null,
        slippage: null,
        createdAt: '2026-03-20T09:00:00',
      },
    ],
    isLoading: false,
    isError: false,
  }),
  usePlacePaperOrderMutation: () => [placeOrderMock, { isLoading: false }],
  useFillPaperOrderMutation: () => [fillOrderMock, { isLoading: false }],
  useCancelPaperOrderMutation: () => [cancelOrderMock, { isLoading: false }],
}));

vi.mock('@/features/settings/exchangeApi', () => ({
  useGetSavedExchangeConnectionsQuery: () => ({
    data: {
      activeConnectionId: 'binance-paper',
      connections: [
        {
          id: 'binance-paper',
          name: 'Binance Paper',
          exchange: 'binance',
          apiKey: '',
          apiSecret: '',
          testnet: true,
          active: true,
        },
      ],
    },
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
        paperEventCount: 1,
        liveEventCount: 0,
        latestEventAt: '2026-03-20T09:00:00',
      },
      events: [
        {
          id: 91,
          actor: 'operator',
          action: 'PAPER_REVIEWED',
          environment: 'paper',
          targetType: 'STRATEGY',
          targetId: '1',
          outcome: 'SUCCESS',
          details: 'Reviewed assigned paper strategy before session open.',
          createdAt: '2026-03-20T09:00:00',
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
        name: 'BTC Momentum Paper',
        type: 'SMA_CROSSOVER',
        status: 'RUNNING',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 0.01,
        maxPositionSize: 0.1,
        profitLoss: 88.45,
        tradeCount: 9,
        currentDrawdown: 3.2,
        paperMode: true,
        shortSellingEnabled: false,
        configVersion: 2,
        lastConfigChangedAt: '2026-03-20T08:00:00',
      },
    ],
  }),
  useGetStrategyConfigHistoryQuery: () => ({
    data: [
      {
        id: 1,
        versionNumber: 2,
        changeReason: 'Tightened paper risk budget.',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 0.01,
        maxPositionSize: 0.1,
        status: 'RUNNING',
        paperMode: true,
        shortSellingEnabled: false,
        changedAt: '2026-03-20T08:00:00',
      },
    ],
  }),
  useStartStrategyMutation: () => [vi.fn(), { isLoading: false }],
  useStopStrategyMutation: () => [vi.fn(), { isLoading: false }],
  useUpdateStrategyConfigMutation: () => [vi.fn(), { isLoading: false }],
}));

vi.mock('@/features/strategies/StrategyConfigModal', () => ({
  StrategyConfigModal: () => <div>Edit Strategy Config</div>,
}));

vi.mock('@/features/trades/tradesApi', () => ({
  useGetTradeHistoryQuery: () => ({
    data: {
      items: [
        {
          id: 20,
          pair: 'BTC/USDT',
          entryTime: '2026-03-20T09:00:00',
          entryPrice: 50000,
          exitTime: '2026-03-20T11:00:00',
          exitPrice: 50500,
          signal: 'BUY',
          positionSide: 'LONG',
          positionSize: 0.05,
          riskAmount: 25,
          pnl: 12.5,
          feesActual: 0.8,
          slippageActual: 0.4,
          stopLoss: 49500,
          takeProfit: 50800,
        },
      ],
    },
  }),
}));

vi.mock('@/features/forwardTesting/ForwardSignalTimelineChart', () => ({
  ForwardSignalTimelineChart: ({ strategyName }: { strategyName: string }) => (
    <div>{strategyName} paper signal chart</div>
  ),
}));

vi.mock('@/components/workspace/ActiveAlgorithmExplainabilityPanel', () => ({
  ActiveAlgorithmExplainabilityPanel: ({
    title,
    summary,
    extraSections = [],
    desktopBehavior,
  }: {
    title: ReactNode;
    summary?: ReactNode;
    extraSections?: Array<{ id: string; title: ReactNode; content: ReactNode }>;
    desktopBehavior?: 'sticky' | 'inline';
  }) => (
    <div
      data-testid="paper-explainability-panel"
      style={{ position: desktopBehavior === 'inline' ? 'relative' : 'sticky' }}
    >
      <div>{title}</div>
      {summary}
      {extraSections.map((section) => (
        <div key={section.id}>
          <div>{section.title}</div>
          <div>{section.content}</div>
        </div>
      ))}
    </div>
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

vi.mock('@/services/api', () => ({
  getApiErrorMessage: () => 'failed',
}));

describe('PaperTradingPage', { timeout: 15000 }, () => {
  const setDesktopInspectorViewport = (desktop: boolean) => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === '(min-width:1200px)' ? desktop : false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));
  };

  const renderPage = () => {
    const store = configureStore({
      reducer: {
        environment: environmentReducer,
      },
      preloadedState: {
        environment: {
          mode: 'test',
          connectedExchange: null,
          lastSyncTime: null,
        },
      },
    });

    return render(
      <Provider store={store}>
        <PaperTradingPage />
      </Provider>
    );
  };

  beforeEach(() => {
    setDesktopInspectorViewport(false);
    placeOrderMock.mockReset();
    fillOrderMock.mockReset();
    cancelOrderMock.mockReset();
    placeOrderMock.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          id: 12,
          status: 'FILLED',
        }),
    });
    fillOrderMock.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          id: 11,
          fillPrice: 50015,
          price: 50000,
        }),
    });
    cancelOrderMock.mockReturnValue({
      unwrap: () => Promise.resolve({ id: 11 }),
    });
  });

  it('renders paper state and order queue', () => {
    renderPage();

    expect(screen.getByText('Paper-only execution')).toBeInTheDocument();
    expect(screen.getByText('Exchange-scoped strategy assignment')).toBeInTheDocument();
    expect(screen.getAllByText('BTC Momentum Paper').length).toBeGreaterThan(0);
    expect(screen.getByText(/paper signal chart/i)).toBeInTheDocument();
    expect(screen.getByText('Paper order events for selected strategy')).toBeInTheDocument();
    expect(screen.getByText('Order entry')).toBeInTheDocument();
    expect(screen.getAllByText('No paper incidents detected.').length).toBeGreaterThan(0);
    expect(screen.getByText('#11')).toBeInTheDocument();
  });

  it('renders the paper detail inspector inline on desktop without the mobile drawer opener', () => {
    setDesktopInspectorViewport(true);
    renderPage();

    expect(screen.queryByRole('button', { name: 'Open active algorithm detail' })).not.toBeInTheDocument();
    expect(screen.getByText('Assigned parameters')).toBeInTheDocument();
    expect(screen.getByText('Recent config versions')).toBeInTheDocument();
    expect(screen.getByText('Paper incidents and audit trail')).toBeInTheDocument();
    expect(screen.getByTestId('paper-explainability-panel')).toHaveStyle({
      position: 'relative',
    });
  });

  it('assigns a strategy to the selected exchange profile locally', async () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: 'Assign to exchange' }));

    await waitFor(() => {
      expect(screen.getByText('BTC Momentum Paper assigned to Binance Paper.')).toBeInTheDocument();
      expect(localStorage.getItem('paper-workspace-assignments')).toContain('binance-paper');
    });
  });

  it('submits a paper order with parsed numeric fields', async () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('Symbol'), {
      target: { value: 'ETH/USDT' },
    });
    fireEvent.change(screen.getByLabelText('Quantity'), {
      target: { value: '0.25' },
    });
    fireEvent.change(screen.getByLabelText('Price'), {
      target: { value: '3100' },
    });
    fireEvent.click(screen.getByRole('button', { name: /submit paper order/i }));

    await waitFor(() => {
      expect(placeOrderMock).toHaveBeenCalledWith({
        symbol: 'ETH/USDT',
        side: 'BUY',
        quantity: 0.25,
        price: 3100,
        executeNow: true,
      });
    });
  });
});
