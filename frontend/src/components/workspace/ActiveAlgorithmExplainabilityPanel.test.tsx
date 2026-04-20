import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ActiveAlgorithmExplainabilityPanel } from './ActiveAlgorithmExplainabilityPanel';

const theme = createTheme();

describe('ActiveAlgorithmExplainabilityPanel', () => {
  it('renders the shared explainability evidence sections', () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === '(min-width:1536px)',
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    render(
      <ThemeProvider theme={theme}>
        <ActiveAlgorithmExplainabilityPanel
          title="Shared algorithm detail"
          subject={{
            name: 'BTC Trend Pullback',
            status: 'RUNNING',
            symbol: 'BTC/USDT',
            timeframe: '1h',
            riskPerTrade: 0.02,
            minPositionSize: 0.01,
            maxPositionSize: 0.1,
            configVersion: 3,
            lastConfigChangedAt: '2026-03-26T08:00:00Z',
          }}
          profile={{
            shortDescription: 'Trend-following pullback entry with reclaim confirmation.',
            entryRule: 'Buy pullbacks after reclaim and trend confirmation.',
            exitRule: 'Exit on failed reclaim or target hit.',
            standAsideRule: 'Stand aside when trend alignment or reclaim confirmation is missing.',
            bestFor: 'Trending sessions with controlled retracements.',
            timeframeGuidance: 'Use 1h bars and review EMA plus RSI overlays together.',
            entryReasons: ['trend aligned', 'pullback confirmed'],
            exitReasons: ['support failed', 'target hit'],
            standAsideReasons: ['trend misaligned', 'reclaim missing'],
            indicatorChecklist: ['ema_20', 'ema_5', 'rsi_5'],
            operatorNotes: ['research only', 'not audit-cleared'],
          }}
          trades={[
            {
              id: 11,
              pair: 'BTC/USDT',
              entryTime: '2026-03-26T09:00:00Z',
              entryPrice: 64000,
              exitTime: null,
              exitPrice: null,
              signal: 'BUY',
              positionSide: 'LONG',
              positionSize: 0.02,
              riskAmount: 18,
              pnl: 9.4,
              stopLoss: 63350,
              takeProfit: 64850,
            },
          ]}
          incidents={[
            {
              id: 'audit-1',
              timestamp: '2026-03-26 09:15',
              title: 'OVERRIDE_REVIEWED success',
              detail: 'Operator reviewed the latest reclaim signal before keeping it active.',
              tone: 'info',
              tags: ['paper', 'operator'],
            },
          ]}
        />
      </ThemeProvider>
    );

    expect(screen.getByText('Entry and exit evidence')).toBeInTheDocument();
    expect(screen.getByText('Signal and decision reason')).toBeInTheDocument();
    expect(screen.getByText('Reason labels and evidence')).toBeInTheDocument();
    expect(screen.getByText('Current risk and PnL stats')).toBeInTheDocument();
    expect(screen.getByText('Position state and exposure')).toBeInTheDocument();
    expect(screen.getByText('Recent incidents or overrides')).toBeInTheDocument();
    expect(screen.getByText(/Buy pullbacks after reclaim and trend confirmation/i)).toBeInTheDocument();
    expect(screen.getByText(/Stand aside when trend alignment or reclaim confirmation is missing/i)).toBeInTheDocument();
    expect(screen.getByText(/trend aligned, pullback confirmed/i)).toBeInTheDocument();
    expect(screen.getByText(/OVERRIDE_REVIEWED success/i)).toBeInTheDocument();
  });
});
