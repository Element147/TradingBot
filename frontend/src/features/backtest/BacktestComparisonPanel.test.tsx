import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { BacktestComparisonPanel } from './BacktestComparisonPanel';

vi.mock('./BacktestComparisonCharts', () => ({
  BacktestComparisonCharts: () => <div>comparison charts</div>,
}));

const comparison = {
  baselineBacktestId: 11,
  items: [
    {
      id: 11,
      strategyId: 'BUY_AND_HOLD',
      datasetName: 'Baseline dataset',
      datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      datasetSchemaVersion: 'ohlcv-v1',
      datasetUploadedAt: '2026-03-01T10:00:00',
      datasetArchived: false,
      symbol: 'BTC/USDT',
      timeframe: '1h',
      executionStatus: 'COMPLETED' as const,
      validationStatus: 'PASSED' as const,
      feesBps: 10,
      slippageBps: 3,
      timestamp: '2026-03-05T10:00:00',
      initialBalance: 1000,
      finalBalance: 1050,
      totalReturnPercent: 5,
      sharpeRatio: 1.1,
      profitFactor: 1.2,
      winRate: 55,
      maxDrawdown: 10,
      totalTrades: 12,
      finalBalanceDelta: 0,
      totalReturnDeltaPercent: 0,
    },
    {
      id: 22,
      strategyId: 'SMA_CROSSOVER',
      datasetName: 'ETH dataset',
      datasetChecksumSha256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
      datasetSchemaVersion: 'ohlcv-v1',
      datasetUploadedAt: '2026-03-01T10:00:00',
      datasetArchived: false,
      symbol: 'ETH/USDT',
      timeframe: '4h',
      executionStatus: 'COMPLETED' as const,
      validationStatus: 'PASSED' as const,
      feesBps: 10,
      slippageBps: 3,
      timestamp: '2026-03-06T10:00:00',
      initialBalance: 1000,
      finalBalance: 980,
      totalReturnPercent: -2,
      sharpeRatio: 0.8,
      profitFactor: 0.9,
      winRate: 44,
      maxDrawdown: 18,
      totalTrades: 30,
      finalBalanceDelta: -70,
      totalReturnDeltaPercent: -7,
    },
    {
      id: 33,
      strategyId: 'BOLLINGER_BANDS',
      datasetName: 'SOL dataset',
      datasetChecksumSha256: 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
      datasetSchemaVersion: 'ohlcv-v1',
      datasetUploadedAt: '2026-03-01T10:00:00',
      datasetArchived: false,
      symbol: 'SOL/USDT',
      timeframe: '1h',
      executionStatus: 'COMPLETED' as const,
      validationStatus: 'PASSED' as const,
      feesBps: 10,
      slippageBps: 3,
      timestamp: '2026-03-04T10:00:00',
      initialBalance: 1000,
      finalBalance: 1200,
      totalReturnPercent: 20,
      sharpeRatio: 1.7,
      profitFactor: 1.9,
      winRate: 60,
      maxDrawdown: 9,
      totalTrades: 20,
      finalBalanceDelta: 150,
      totalReturnDeltaPercent: 15,
    },
  ],
};

describe('BacktestComparisonPanel', () => {
  it('keeps the baseline pinned first while sorting the remaining rows', async () => {
    const user = userEvent.setup();
    render(<BacktestComparisonPanel comparison={comparison} />);

    const table = screen.getByRole('table');
    let bodyRows = table.querySelectorAll('tbody tr');

    expect(bodyRows[0]).toHaveTextContent('#11');

    await user.click(screen.getByRole('button', { name: /Final balance/i }));

    bodyRows = table.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('#11');
    expect(bodyRows[1]).toHaveTextContent('#22');
    expect(bodyRows[2]).toHaveTextContent('#33');

    await user.click(screen.getByRole('button', { name: /Final balance/i }));

    bodyRows = table.querySelectorAll('tbody tr');
    expect(bodyRows[0]).toHaveTextContent('#11');
    expect(bodyRows[1]).toHaveTextContent('#33');
    expect(bodyRows[2]).toHaveTextContent('#22');
  }, 15000);
});
