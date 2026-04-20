import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { BalanceCard } from './BalanceCard';

import { useGetBalanceQuery } from '@/features/account/accountApi';

vi.mock('@/features/account/accountApi', () => ({
  useGetBalanceQuery: vi.fn(),
}));

const mockUseGetBalanceQuery = vi.mocked(useGetBalanceQuery);

const mockBalanceData = {
  total: '1250.50',
  available: '1000.00',
  locked: '250.50',
  assets: [
    { symbol: 'USDT', amount: '500.00', valueUSD: '500.00' },
    { symbol: 'BTC', amount: '0.01500000', valueUSD: '600.00' },
    { symbol: 'ETH', amount: '0.08000000', valueUSD: '150.50' },
  ],
  lastSync: '2026-03-09T10:30:00Z',
};

const setBalanceQueryResult = (overrides: Partial<ReturnType<typeof mockUseGetBalanceQuery>> = {}) => {
  mockUseGetBalanceQuery.mockReturnValue({
    data: mockBalanceData,
    isLoading: false,
    error: null,
    refetch: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  });
};

describe('BalanceCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setBalanceQueryResult();
  });

  it('should display error state when balance fetch fails', () => {
    setBalanceQueryResult({ error: { message: 'Network error' }, data: undefined });
    render(<BalanceCard />);
    expect(screen.getByText('Network error')).toBeInTheDocument();
  });

  it('should display total balance correctly', () => {
    render(<BalanceCard />);
    expect(screen.getByText('Total Balance')).toBeInTheDocument();
    expect(screen.getByText('$1,250.50')).toBeInTheDocument();
  });

  it('should display available balance correctly', () => {
    render(<BalanceCard />);
    expect(screen.getByText('Available')).toBeInTheDocument();
    expect(screen.getByText('$1,000.00')).toBeInTheDocument();
  });

  it('should display locked balance correctly', () => {
    render(<BalanceCard />);
    expect(screen.getByText('Locked')).toBeInTheDocument();
    expect(screen.getByText('$250.50')).toBeInTheDocument();
  });

  it('should display all asset breakdowns', () => {
    render(<BalanceCard />);
    expect(screen.getByText('Asset Breakdown')).toBeInTheDocument();
    expect(screen.getByText('USDT')).toBeInTheDocument();
    expect(screen.getByText('BTC')).toBeInTheDocument();
    expect(screen.getByText('ETH')).toBeInTheDocument();
  });

  it('should display asset amounts correctly', () => {
    render(<BalanceCard />);
    expect(screen.getByText('500')).toBeInTheDocument();
    expect(screen.getByText('0.015')).toBeInTheDocument();
    expect(screen.getByText('0.08')).toBeInTheDocument();
  });

  it('should display asset USD values correctly', () => {
    render(<BalanceCard />);
    expect(screen.getByText('$500.00')).toBeInTheDocument();
    expect(screen.getByText('$600.00')).toBeInTheDocument();
    expect(screen.getByText('$150.50')).toBeInTheDocument();
  });

  it('should display last sync timestamp', () => {
    render(<BalanceCard />);
    expect(screen.getByText(/Last updated:/i)).toBeInTheDocument();
  });

  it('should render refresh button', () => {
    render(<BalanceCard />);
    const refreshButton = screen.getByRole('button', { name: /refresh balance/i });
    expect(refreshButton).toBeInTheDocument();
  });

  it('should call refetch when refresh button is clicked', async () => {
    const refetch = vi.fn().mockResolvedValue(undefined);
    setBalanceQueryResult({ refetch });
    render(<BalanceCard />);
    fireEvent.click(screen.getByRole('button', { name: /refresh balance/i }));
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(1));
  });

  it('should display card title', () => {
    render(<BalanceCard />);
    expect(screen.getByText('Account Balance')).toBeInTheDocument();
  });

  it('should handle empty asset list', () => {
    setBalanceQueryResult({ data: { ...mockBalanceData, assets: [] } });
    render(<BalanceCard />);
    expect(screen.getByText('Asset Breakdown')).toBeInTheDocument();
  });

  it('should handle zero balances', () => {
    setBalanceQueryResult({
      data: { total: '0.00', available: '0.00', locked: '0.00', assets: [], lastSync: mockBalanceData.lastSync },
    });
    render(<BalanceCard />);
    expect(screen.getAllByText('$0.00').length).toBeGreaterThan(0);
  });

  it('should format timestamp as locale string', () => {
    render(<BalanceCard />);
    expect(screen.getByText(/Last updated: Mar/i)).toBeInTheDocument();
  });

  it('should display multiple assets in correct order', () => {
    render(<BalanceCard />);
    expect(screen.getByText('USDT')).toBeInTheDocument();
    expect(screen.getByText('BTC')).toBeInTheDocument();
    expect(screen.getByText('ETH')).toBeInTheDocument();
  });

  it('should have proper accessibility labels', () => {
    render(<BalanceCard />);
    const refreshButton = screen.getByRole('button', { name: /refresh balance/i });
    expect(refreshButton).toHaveAttribute('aria-label', 'Refresh balance');
  });

  it('should render within a surface panel', () => {
    const { container } = render(<BalanceCard />);
    const surface = container.querySelector('.MuiPaper-root');
    expect(surface).toBeInTheDocument();
  });
});
