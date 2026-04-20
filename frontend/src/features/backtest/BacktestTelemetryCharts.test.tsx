import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { BacktestComparisonCharts } from './BacktestComparisonCharts';
import { BacktestExposureChart } from './BacktestExposureChart';
import { BacktestPriceActionChart } from './BacktestPriceActionChart';

const telemetry = {
  symbol: 'BTC/USDT',
  points: [
    {
      timestamp: '2026-01-01T00:00:00Z',
      open: 100,
      high: 101,
      low: 99,
      close: 100,
      volume: 1000,
      exposurePct: 0,
      regime: 'WARMUP' as const,
    },
    {
      timestamp: '2026-01-02T00:00:00Z',
      open: 102,
      high: 103,
      low: 101,
      close: 102,
      volume: 1100,
      exposurePct: 75,
      regime: 'TREND_UP' as const,
    },
  ],
  actions: [
    {
      timestamp: '2026-01-02T00:00:00Z',
      action: 'BUY' as const,
      price: 102,
      label: 'Long entry',
    },
  ],
  indicators: [
    {
      key: 'ema_20',
      label: 'EMA (20)',
      pane: 'PRICE' as const,
      points: [
        { timestamp: '2026-01-01T00:00:00Z', value: null },
        { timestamp: '2026-01-02T00:00:00Z', value: 101 },
      ],
    },
  ],
};

describe('backtest telemetry charts', () => {
  it('renders price-action chart table mode', () => {
    render(<BacktestPriceActionChart series={telemetry} />);

    expect(screen.getByText(/price and actions/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Table' }));
    expect(screen.getByRole('columnheader', { name: 'Exposure %' })).toBeInTheDocument();
  });

  it('renders exposure chart table mode', () => {
    render(<BacktestExposureChart series={telemetry} />);

    expect(screen.getByText(/exposure and regime/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Table' }));
    expect(screen.getByRole('columnheader', { name: 'Regime' })).toBeInTheDocument();
  });

  it('renders comparison charts', () => {
    render(
      <BacktestComparisonCharts
        comparison={{
          baselineBacktestId: 42,
          items: [
            {
              id: 42,
              strategyId: 'BOLLINGER_BANDS',
              datasetName: 'BTC 1h 2025',
              datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
              datasetSchemaVersion: 'ohlcv-v1',
              datasetUploadedAt: '2026-03-11T10:00:00',
              datasetArchived: false,
              symbol: 'BTC/USDT',
              timeframe: '1h',
              executionStatus: 'COMPLETED',
              validationStatus: 'PASSED',
              feesBps: 10,
              slippageBps: 3,
              timestamp: '2026-03-10T10:00:00',
              initialBalance: 1000,
              finalBalance: 1080,
              totalReturnPercent: 8,
              sharpeRatio: 1.2,
              profitFactor: 1.6,
              winRate: 52,
              maxDrawdown: 18,
              totalTrades: 80,
              finalBalanceDelta: 0,
              totalReturnDeltaPercent: 0,
            },
            {
              id: 43,
              strategyId: 'SMA_CROSSOVER',
              datasetName: 'BTC 1h 2025',
              datasetChecksumSha256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              datasetSchemaVersion: 'ohlcv-v1',
              datasetUploadedAt: '2026-03-11T10:00:00',
              datasetArchived: false,
              symbol: 'BTC/USDT',
              timeframe: '1h',
              executionStatus: 'COMPLETED',
              validationStatus: 'FAILED',
              feesBps: 10,
              slippageBps: 3,
              timestamp: '2026-03-11T10:00:00',
              initialBalance: 1000,
              finalBalance: 950,
              totalReturnPercent: -5,
              sharpeRatio: 0.8,
              profitFactor: 1.1,
              winRate: 45,
              maxDrawdown: 22,
              totalTrades: 48,
              finalBalanceDelta: -130,
              totalReturnDeltaPercent: -13,
            },
          ],
        }}
      />
    );

    expect(screen.getByText('Comparison Returns')).toBeInTheDocument();
    expect(screen.getByText('Comparison Risk')).toBeInTheDocument();
  });
});
