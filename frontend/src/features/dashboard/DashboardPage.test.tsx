import { configureStore } from '@reduxjs/toolkit';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import authReducer from '../auth/authSlice';
import environmentReducer from '../environment/environmentSlice';

import DashboardPage from './DashboardPage';

vi.mock('./BalanceCard', () => ({
  BalanceCard: () => <div data-testid="balance-card">Balance Card</div>,
}));

vi.mock('./PerformanceCard', () => ({
  PerformanceCard: () => <div data-testid="performance-card">Performance Card</div>,
}));

vi.mock('./PaperTradingCard', () => ({
  PaperTradingCard: () => <div data-testid="paper-trading-card">Paper Trading</div>,
}));

vi.mock('./PositionsList', () => ({
  PositionsList: () => <div data-testid="positions-list">Positions List</div>,
}));

vi.mock('./RecentTradesList', () => ({
  RecentTradesList: () => <div data-testid="recent-trades-list">Recent Trades</div>,
}));

vi.mock('./SystemHealthIndicator', () => ({
  SystemHealthIndicator: () => <div data-testid="system-health">System Health</div>,
}));

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="app-layout">{children}</div>
  ),
}));

describe('DashboardPage', () => {
  const createMockStore = () =>
    configureStore({
      reducer: {
        auth: authReducer,
        environment: environmentReducer,
      },
      preloadedState: {
        auth: {
          token: 'mock-token',
          refreshToken: null,
          user: null,
          isAuthenticated: true,
          loading: false,
          error: null,
          sessionTimeout: null,
          lastActivity: Date.now(),
        },
        environment: {
          mode: 'test',
          connectedExchange: null,
          lastSyncTime: null,
        },
      },
    });

  it('should render dashboard page intro content', () => {
    const store = createMockStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Start here')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open Backtest' })).toBeInTheDocument();
    expect(screen.getByText('Research Workstation')).toBeInTheDocument();
    expect(screen.getByText('Safety defaults')).toBeInTheDocument();
    expect(screen.getByText('Workstation flow')).toBeInTheDocument();
    expect(screen.getByText('1. Review Dashboard')).toBeInTheDocument();
    expect(screen.getByText('4. Monitor in Live')).toBeInTheDocument();
  });

  it('should render within AppLayout', () => {
    const store = createMockStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByTestId('app-layout')).toBeInTheDocument();
  });

  it('should render BalanceCard component', () => {
    const store = createMockStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByTestId('balance-card')).toBeInTheDocument();
  });

  it('should render PerformanceCard component', () => {
    const store = createMockStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByTestId('performance-card')).toBeInTheDocument();
  });

  it('should render both cards in grid layout', () => {
    const store = createMockStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </Provider>
    );

    const balanceCard = screen.getByTestId('balance-card');
    const performanceCard = screen.getByTestId('performance-card');

    expect(balanceCard).toBeInTheDocument();
    expect(performanceCard).toBeInTheDocument();
    expect(screen.getByTestId('paper-trading-card')).toBeInTheDocument();
  });
});
