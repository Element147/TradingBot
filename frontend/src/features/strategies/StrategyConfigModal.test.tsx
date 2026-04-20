import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { StrategyConfigModal } from './StrategyConfigModal';

vi.mock('./strategiesApi', () => ({
  useGetStrategyConfigHistoryQuery: () => ({
    data: [
      {
        id: 11,
        versionNumber: 2,
        changeReason: 'Updated symbol, timeframe',
        symbol: 'ETH/USDT',
        timeframe: '4h',
        riskPerTrade: 0.03,
        minPositionSize: 20,
        maxPositionSize: 120,
        status: 'STOPPED',
        paperMode: true,
        shortSellingEnabled: true,
        changedAt: '2026-03-12T10:00:00',
      },
      {
        id: 10,
        versionNumber: 1,
        changeReason: 'Seeded default strategy configuration',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 10,
        maxPositionSize: 100,
        status: 'STOPPED',
        paperMode: true,
        shortSellingEnabled: true,
        changedAt: '2026-03-11T10:00:00',
      },
    ],
  }),
}));

describe('StrategyConfigModal', () => {
  it('renders config history entries', { timeout: 10000 }, () => {
    render(
      <StrategyConfigModal
        strategy={{
          id: 1,
          name: 'Bollinger BTC Mean Reversion',
          type: 'bollinger-bands',
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
          configVersion: 2,
          lastConfigChangedAt: '2026-03-12T10:00:00',
        }}
        busy={false}
        onClose={() => undefined}
        onSave={() => undefined}
      />
    );

    expect(screen.getByText('Config Version History')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply Strategy Defaults' })).toBeInTheDocument();
    expect(screen.getByText('Short Selling Enabled')).toBeInTheDocument();
    expect(screen.getByText(/v2: Updated symbol, timeframe/)).toBeInTheDocument();
    expect(screen.getByText(/ETH\/USDT \(4h\) \| Risk 3.00% \| Size 20 - 120 \| Short On/)).toBeInTheDocument();
  });
});
