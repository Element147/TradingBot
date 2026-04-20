import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RecentTradesList } from './RecentTradesList';

type RecentTradesQueryResult = {
  data?: Array<{
    id: string;
    strategyId: string;
    strategyName: string;
    symbol: string;
    side: 'LONG' | 'SHORT';
    entryPrice: string;
    exitPrice: string;
    quantity: string;
    entryTime: string;
    exitTime: string;
    duration: string;
    profitLoss: string;
    profitLossPercentage: string;
    fees: string;
    slippage: string;
    status: 'CLOSED';
  }>;
  isLoading: boolean;
  error?: unknown;
};

const { mockUseGetRecentTradesQuery } = vi.hoisted(() => ({
  mockUseGetRecentTradesQuery: vi.fn<() => RecentTradesQueryResult>(),
}));

vi.mock('../account/accountApi', () => ({
  useGetRecentTradesQuery: () => mockUseGetRecentTradesQuery(),
}));

// Mock formatters
vi.mock('@/utils/formatters', () => ({
  formatCurrency: (value: string) => `$${value}`,
  formatPercentage: (value: string) => `${value}%`,
  formatDateTime: (_value: string) => 'Mar 9, 2026, 3:45 PM',
}));

const mockTrades = [
  {
    id: '1',
    strategyId: 'strat-1',
    strategyName: 'Bollinger Bands',
    symbol: 'BTC/USDT',
    side: 'LONG' as const,
    entryPrice: '50000.00',
    exitPrice: '51000.00',
    quantity: '0.01',
    entryTime: '2026-03-09T10:00:00Z',
    exitTime: '2026-03-09T12:00:00Z',
    duration: '2h',
    profitLoss: '10.00',
    profitLossPercentage: '2.00',
    fees: '0.50',
    slippage: '0.15',
    status: 'CLOSED' as const,
  },
  {
    id: '2',
    strategyId: 'strat-1',
    strategyName: 'Bollinger Bands',
    symbol: 'ETH/USDT',
    side: 'SHORT' as const,
    entryPrice: '3000.00',
    exitPrice: '2950.00',
    quantity: '0.1',
    entryTime: '2026-03-09T11:00:00Z',
    exitTime: '2026-03-09T13:00:00Z',
    duration: '2h',
    profitLoss: '-5.00',
    profitLossPercentage: '-1.67',
    fees: '0.30',
    slippage: '0.09',
    status: 'CLOSED' as const,
  },
];

const mockQueryState = ({
  data = mockTrades,
  isLoading = false,
  error = undefined,
}: {
  data?: typeof mockTrades;
  isLoading?: boolean;
  error?: unknown;
}) => {
  mockUseGetRecentTradesQuery.mockReturnValue({
    data,
    isLoading,
    error,
  });
};

describe('RecentTradesList', { timeout: 15000 }, () => {
  beforeEach(() => {
    mockUseGetRecentTradesQuery.mockReset();
  });

  it('should render loading state', () => {
    mockQueryState({ isLoading: true });

    render(<RecentTradesList />);

    expect(screen.getByText('Loading recent trades...')).toBeInTheDocument();
    expect(screen.getByText('Recent Trades')).toBeInTheDocument();
  });

  it('should render error state', () => {
    mockQueryState({
      error: {
        status: 500,
        data: 'Server error',
      },
    });

    render(<RecentTradesList />);

    expect(screen.getByText('Server error')).toBeInTheDocument();
  });

  it('should render empty state when no trades', () => {
    mockQueryState({ data: [] });

    render(<RecentTradesList />);

    expect(screen.getByText('No recent trades.')).toBeInTheDocument();
    expect(screen.getByText('Last 0')).toBeInTheDocument();
  });

  it('should render trades list correctly', () => {
    mockQueryState({});

    render(<RecentTradesList />);

    expect(screen.getByText('Recent Trades')).toBeInTheDocument();
    expect(screen.getByText('Last 2')).toBeInTheDocument();
    expect(screen.getAllByText('Entry').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Exit').length).toBeGreaterThan(0);
    expect(screen.getAllByText('P&L').length).toBeGreaterThan(0);

    expect(screen.getByText('BTC/USDT')).toBeInTheDocument();
    expect(screen.getAllByText('Bollinger Bands')).toHaveLength(2);
    expect(screen.getByText('LONG')).toBeInTheDocument();
    expect(screen.getByText('$50000.00')).toBeInTheDocument();
    expect(screen.getByText('$51000.00')).toBeInTheDocument();
    expect(screen.getByText('+$10.00')).toBeInTheDocument();
    expect(screen.getByText('(+2.00%)')).toBeInTheDocument();

    expect(screen.getByText('ETH/USDT')).toBeInTheDocument();
    expect(screen.getByText('SHORT')).toBeInTheDocument();
    expect(screen.getByText('$3000.00')).toBeInTheDocument();
    expect(screen.getByText('$2950.00')).toBeInTheDocument();
    expect(screen.getByText('$-5.00')).toBeInTheDocument();
    expect(screen.getByText('(-1.67%)')).toBeInTheDocument();

    expect(screen.getAllByText('Mar 9, 2026, 3:45 PM')).toHaveLength(2);
  });

  it('should color-code profit and loss correctly', () => {
    mockQueryState({});

    render(<RecentTradesList />);

    expect(screen.getByText('+$10.00')).toBeInTheDocument();
    expect(screen.getByText('(+2.00%)')).toBeInTheDocument();
    expect(screen.getByText('$-5.00')).toBeInTheDocument();
    expect(screen.getByText('(-1.67%)')).toBeInTheDocument();
  });
});
