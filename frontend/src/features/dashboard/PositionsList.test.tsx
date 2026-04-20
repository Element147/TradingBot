import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PositionsList } from './PositionsList';

type OpenPositionsQueryResult = {
  data?: Array<{
    id: string;
    strategyId: string;
    strategyName: string;
    symbol: string;
    side: 'LONG' | 'SHORT';
    entryPrice: string;
    currentPrice: string;
    quantity: string;
    entryTime: string;
    unrealizedPnL: string;
    unrealizedPnLPercentage: string;
    status: 'OPEN';
  }>;
  isLoading: boolean;
  error?: unknown;
};

const { mockUseGetOpenPositionsQuery } = vi.hoisted(() => ({
  mockUseGetOpenPositionsQuery: vi.fn<() => OpenPositionsQueryResult>(),
}));

vi.mock('../account/accountApi', () => ({
  useGetOpenPositionsQuery: () => mockUseGetOpenPositionsQuery(),
}));

// Mock formatters
vi.mock('@/utils/formatters', () => ({
  formatCurrency: (value: string) => `$${value}`,
  formatCompactNumber: (value: string) => value.replace(/\.?0+$/, ''),
  formatPercentage: (value: string) => `${value}%`,
}));

const mockPositions = [
  {
    id: '1',
    strategyId: 'strat-1',
    strategyName: 'Bollinger Bands',
    symbol: 'BTC/USDT',
    side: 'LONG' as const,
    entryPrice: '50000.00',
    currentPrice: '51000.00',
    quantity: '0.01',
    entryTime: '2026-03-09T10:00:00Z',
    unrealizedPnL: '10.00',
    unrealizedPnLPercentage: '2.00',
    status: 'OPEN' as const,
  },
  {
    id: '2',
    strategyId: 'strat-1',
    strategyName: 'Bollinger Bands',
    symbol: 'ETH/USDT',
    side: 'SHORT' as const,
    entryPrice: '3000.00',
    currentPrice: '3050.00',
    quantity: '0.1',
    entryTime: '2026-03-09T11:00:00Z',
    unrealizedPnL: '-5.00',
    unrealizedPnLPercentage: '-1.67',
    status: 'OPEN' as const,
  },
];

const mockQueryState = ({
  data = mockPositions,
  isLoading = false,
  error = undefined,
}: {
  data?: typeof mockPositions;
  isLoading?: boolean;
  error?: unknown;
}) => {
  mockUseGetOpenPositionsQuery.mockReturnValue({
    data,
    isLoading,
    error,
  });
};

describe('PositionsList', () => {
  beforeEach(() => {
    mockUseGetOpenPositionsQuery.mockReset();
  });

  it('should render loading state', () => {
    mockQueryState({ isLoading: true });

    render(<PositionsList />);

    expect(screen.getByText('Loading positions...')).toBeInTheDocument();
    expect(screen.getByText('Open Positions')).toBeInTheDocument();
  });

  it('should render error state', () => {
    mockQueryState({
      error: {
        status: 500,
        data: 'Server error',
      },
    });

    render(<PositionsList />);

    expect(screen.getByText('Server error')).toBeInTheDocument();
  });

  it('should render empty state when no positions', () => {
    mockQueryState({ data: [] });

    render(<PositionsList />);

    expect(screen.getByText('No open positions.')).toBeInTheDocument();
    expect(screen.getByText('0 Positions')).toBeInTheDocument();
  });

  it('should render positions list correctly', () => {
    mockQueryState({});

    render(<PositionsList />);

    expect(screen.getByText('Open Positions')).toBeInTheDocument();
    expect(screen.getByText('2 Positions')).toBeInTheDocument();
    expect(screen.getAllByText('Entry').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Current').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Quantity').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Unrealized P&L').length).toBeGreaterThan(0);

    expect(screen.getByText('BTC/USDT')).toBeInTheDocument();
    expect(screen.getAllByText('Bollinger Bands')).toHaveLength(2);
    expect(screen.getByText('LONG')).toBeInTheDocument();
    expect(screen.getByText('$50000.00')).toBeInTheDocument();
    expect(screen.getByText('$51000.00')).toBeInTheDocument();
    expect(screen.getByText('0.01')).toBeInTheDocument();
    expect(screen.getByText('+$10.00')).toBeInTheDocument();
    expect(screen.getByText('(+2.00%)')).toBeInTheDocument();

    expect(screen.getByText('ETH/USDT')).toBeInTheDocument();
    expect(screen.getByText('SHORT')).toBeInTheDocument();
    expect(screen.getByText('$3000.00')).toBeInTheDocument();
    expect(screen.getByText('$3050.00')).toBeInTheDocument();
    expect(screen.getByText('0.1')).toBeInTheDocument();
    expect(screen.getByText('$-5.00')).toBeInTheDocument();
    expect(screen.getByText('(-1.67%)')).toBeInTheDocument();
  });

  it('should display correct position count in singular form', () => {
    mockQueryState({ data: [mockPositions[0]] });

    render(<PositionsList />);

    expect(screen.getByText('1 Position')).toBeInTheDocument();
  });

  it('should color-code profit and loss correctly', () => {
    mockQueryState({});

    render(<PositionsList />);

    expect(screen.getByText('+$10.00')).toBeInTheDocument();
    expect(screen.getByText('(+2.00%)')).toBeInTheDocument();
    expect(screen.getByText('$-5.00')).toBeInTheDocument();
    expect(screen.getByText('(-1.67%)')).toBeInTheDocument();
  });
});
