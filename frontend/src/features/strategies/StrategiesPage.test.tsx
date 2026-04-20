import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StrategiesPage from './StrategiesPage';

const startMutation = vi.fn(() => ({ unwrap: vi.fn().mockResolvedValue({}) }));
const stopMutation = vi.fn(() => ({ unwrap: vi.fn().mockResolvedValue({}) }));
const updateMutation = vi.fn(() => ({ unwrap: vi.fn().mockResolvedValue({}) }));
const getStrategiesQueryMock = vi.fn<(arg: unknown, options: unknown) => void>();

const baseStrategiesQueryResult = {
  data: [
    {
      id: 1,
      name: 'Bollinger BTC Mean Reversion',
      type: 'BOLLINGER_BANDS',
      status: 'STOPPED',
      symbol: 'BTC/USDT',
      timeframe: '1h',
      riskPerTrade: 0.02,
      minPositionSize: 10,
      maxPositionSize: 100,
      profitLoss: 0,
      tradeCount: 0,
      currentDrawdown: 0,
      paperMode: true,
      shortSellingEnabled: true,
      configVersion: 1,
      lastConfigChangedAt: '2026-03-12T10:00:00',
    },
    {
      id: 2,
      name: 'SMA BTC Trend',
      type: 'SMA_CROSSOVER',
      status: 'STOPPED',
      symbol: 'BTC/USDT',
      timeframe: '4h',
      riskPerTrade: 0.02,
      minPositionSize: 10,
      maxPositionSize: 100,
      profitLoss: 12.5,
      tradeCount: 3,
      currentDrawdown: 0,
      paperMode: true,
      shortSellingEnabled: false,
      configVersion: 2,
      lastConfigChangedAt: '2026-03-15T10:00:00',
    },
  ],
  isLoading: false,
  isError: false,
  error: null,
};

let strategiesQueryResult = baseStrategiesQueryResult;

vi.mock('./strategiesApi', () => ({
  PAPER_STRATEGIES_QUERY: { executionContext: 'paper' },
  useGetStrategiesQuery: (arg: unknown, options: unknown) => {
    getStrategiesQueryMock(arg, options);
    return strategiesQueryResult;
  },
  useStartStrategyMutation: () => [startMutation, { isLoading: false }],
  useStopStrategyMutation: () => [stopMutation, { isLoading: false }],
  useUpdateStrategyConfigMutation: () => [updateMutation, { isLoading: false }],
}));

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

describe('StrategiesPage', () => {
  beforeEach(() => {
    getStrategiesQueryMock.mockReset();
    strategiesQueryResult = baseStrategiesQueryResult;
  });

  it('renders grouped strategy sections with operator-first defaults', async () => {
    const user = userEvent.setup();
    render(<StrategiesPage />);

    expect(screen.getByText('Paper-safe strategy desk')).toBeInTheDocument();
    expect(getStrategiesQueryMock).toHaveBeenCalledWith(
      { executionContext: 'paper' },
      expect.objectContaining({
        pollingInterval: 30000,
        skipPollingIfUnfocused: true,
      })
    );
    expect(screen.getByRole('tab', { name: 'Saved configs' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Strategy guide' })).toBeInTheDocument();
    expect(screen.getAllByText('Archive candidate').length).toBeGreaterThan(0);
    expect(screen.getByText('Shadow Paper')).toBeInTheDocument();
    expect(screen.getByText('Paper-monitor candidates')).toBeInTheDocument();
    const archiveAccordion = screen.getByRole('button', { name: /Archive candidates/i });
    expect(archiveAccordion).toBeInTheDocument();
    expect(archiveAccordion).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByText('SMA BTC Trend')).toBeInTheDocument();

    await user.click(archiveAccordion);

    expect(archiveAccordion).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Bollinger BTC Mean Reversion')).toBeInTheDocument();
    expect(screen.getByText('BTC/USDT (1h)')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Edit config' }).length).toBeGreaterThan(0);
  }, 15000);

  it('shows the guide only when the guide tab is opened', async () => {
    const user = userEvent.setup();
    render(<StrategiesPage />);

    expect(screen.queryByText('Buy and Hold Baseline')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Strategy guide' }));

    expect(screen.getByText('Buy and Hold Baseline')).toBeInTheDocument();
  });

  it('shows start confirmation dialog before mutation execution', async () => {
    const user = userEvent.setup();
    render(<StrategiesPage />);

    await user.click(screen.getByRole('button', { name: 'Start' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/Are you sure you want to start/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(startMutation).toHaveBeenCalledWith(2);
  }, 15000);

  it('shows a helpful network error message when the strategy service is unreachable', () => {
    strategiesQueryResult = {
      data: [],
      isLoading: false,
      isError: true,
      error: { status: 'FETCH_ERROR' },
    };

    render(<StrategiesPage />);

    expect(screen.getByText(/Unable to reach the strategy service/i)).toBeInTheDocument();
  });
});
