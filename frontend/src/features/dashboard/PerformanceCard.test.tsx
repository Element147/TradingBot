import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PerformanceCard } from './PerformanceCard';

import { useGetPerformanceQuery } from '@/features/account/accountApi';

vi.mock('@/features/account/accountApi', () => ({
  useGetPerformanceQuery: vi.fn(),
}));

const mockUseGetPerformanceQuery = vi.mocked(useGetPerformanceQuery);

const mockPerformanceData = {
  totalProfitLoss: '125.50',
  profitLossPercentage: '12.55',
  winRate: '55.5',
  tradeCount: 42,
  cashRatio: '1.25',
};

const mockLossData = {
  totalProfitLoss: '-75.25',
  profitLossPercentage: '-7.52',
  winRate: '45.0',
  tradeCount: 30,
  cashRatio: '0.85',
};

const setPerformanceQueryResult = (
  data = mockPerformanceData,
  isLoading = false,
  error: unknown = null
) => {
  mockUseGetPerformanceQuery.mockImplementation(() => ({
    data,
    isLoading,
    error,
  }));
};

describe('PerformanceCard', { timeout: 15000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setPerformanceQueryResult();
  });

  it('should display error state when performance fetch fails', () => {
    setPerformanceQueryResult(mockPerformanceData, false, { message: 'Network error' });
    render(<PerformanceCard />);
    expect(screen.getByText('Network error')).toBeInTheDocument();
  });

  it('should display card title', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('Performance')).toBeInTheDocument();
  });

  it('should display all timeframe buttons', () => {
    render(<PerformanceCard />);
    expect(screen.getByRole('button', { name: /today/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /this week/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /this month/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /all time/i })).toBeInTheDocument();
  });

  it('should have "today" selected by default', () => {
    render(<PerformanceCard />);
    expect(screen.getByRole('button', { name: /today/i })).toHaveClass('Mui-selected');
  });

  it('should change timeframe when button is clicked', async () => {
    render(<PerformanceCard />);
    const weekButton = screen.getByRole('button', { name: /this week/i });
    fireEvent.click(weekButton);
    await waitFor(() => expect(weekButton).toHaveClass('Mui-selected'));
    expect(mockUseGetPerformanceQuery).toHaveBeenCalledWith('week');
  });

  it('should display profit values', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('$125.50')).toBeInTheDocument();
  });

  it('should display loss values', () => {
    setPerformanceQueryResult(mockLossData);
    render(<PerformanceCard />);
    expect(screen.getByText('-$75.25')).toBeInTheDocument();
  });

  it('should display total profit/loss correctly', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('Total Profit/Loss')).toBeInTheDocument();
    expect(screen.getByText('$125.50')).toBeInTheDocument();
  });

  it('should display profit/loss percentage with plus sign for profit', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('+12.55%')).toBeInTheDocument();
  });

  it('should display profit/loss percentage without plus sign for loss', () => {
    setPerformanceQueryResult(mockLossData);
    render(<PerformanceCard />);
    expect(screen.getByText('-7.52%')).toBeInTheDocument();
  });

  it('should display win rate correctly', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('Win Rate')).toBeInTheDocument();
    expect(screen.getByText('55.50%')).toBeInTheDocument();
  });

  it('should display trade count correctly', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('Trade Count')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
  });

  it('should display cash ratio correctly', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('Cash to Invested Capital Ratio')).toBeInTheDocument();
    expect(screen.getByText('1.25')).toBeInTheDocument();
  });

  it('should handle zero profit/loss', () => {
    setPerformanceQueryResult({ ...mockPerformanceData, totalProfitLoss: '0.00', profitLossPercentage: '0.00' });
    render(<PerformanceCard />);
    expect(screen.getByText('$0.00')).toBeInTheDocument();
    expect(screen.getByText('+0.00%')).toBeInTheDocument();
  });

  it('should handle negative profit/loss correctly', () => {
    setPerformanceQueryResult(mockLossData);
    render(<PerformanceCard />);
    expect(screen.getByText('-$75.25')).toBeInTheDocument();
  });

  it('should switch between all timeframes', async () => {
    render(<PerformanceCard />);
    const timeframes = [/this week/i, /this month/i, /all time/i, /today/i];
    for (const timeframe of timeframes) {
      const button = screen.getByRole('button', { name: timeframe });
      fireEvent.click(button);
      await waitFor(() => expect(button).toHaveClass('Mui-selected'));
    }
  });

  it('should not change timeframe when clicking already selected button', () => {
    render(<PerformanceCard />);
    const todayButton = screen.getByRole('button', { name: /today/i });
    fireEvent.click(todayButton);
    expect(todayButton).toHaveClass('Mui-selected');
  });

  it('should render within a surface panel', () => {
    const { container } = render(<PerformanceCard />);
    expect(container.querySelector('.MuiPaper-root')).toBeInTheDocument();
  });

  it('should display metrics in a grid layout', () => {
    render(<PerformanceCard />);
    expect(screen.getByText('Win Rate')).toBeInTheDocument();
    expect(screen.getByText('Trade Count')).toBeInTheDocument();
    expect(screen.getByText('Cash to Invested Capital Ratio')).toBeInTheDocument();
  });

  it('should have proper ARIA labels for timeframe buttons', () => {
    render(<PerformanceCard />);
    expect(screen.getByRole('button', { name: /today/i })).toHaveAttribute('aria-label', 'Today');
    expect(screen.getByRole('button', { name: /this week/i })).toHaveAttribute('aria-label', 'This week');
    expect(screen.getByRole('button', { name: /this month/i })).toHaveAttribute('aria-label', 'This month');
    expect(screen.getByRole('button', { name: /all time/i })).toHaveAttribute('aria-label', 'All time');
  });

  it('should handle very large profit values', () => {
    setPerformanceQueryResult({
      ...mockPerformanceData,
      totalProfitLoss: '999999.99',
      profitLossPercentage: '999.99',
    });
    render(<PerformanceCard />);
    expect(screen.getByText('$999,999.99')).toBeInTheDocument();
    expect(screen.getByText('+999.99%')).toBeInTheDocument();
  });

  it('should handle very large loss values', () => {
    setPerformanceQueryResult({
      ...mockPerformanceData,
      totalProfitLoss: '-999999.99',
      profitLossPercentage: '-999.99',
    });
    render(<PerformanceCard />);
    expect(screen.getByText('-$999,999.99')).toBeInTheDocument();
    expect(screen.getByText('-999.99%')).toBeInTheDocument();
  });
});
