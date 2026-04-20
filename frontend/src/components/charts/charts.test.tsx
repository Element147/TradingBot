import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DrawdownChart } from './DrawdownChart';
import { EquityCurve } from './EquityCurve';

describe('chart components', () => {
  it('renders equity curve with timeframe controls', () => {
    render(
      <EquityCurve
        points={[
          { timestamp: '2026-01-01T00:00:00Z', equity: 1000 },
          { timestamp: '2026-01-02T00:00:00Z', equity: 1020 },
        ]}
      />
    );

    expect(screen.getByText('Equity Curve')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1d' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Table' }));
    expect(screen.getByRole('columnheader', { name: 'Timestamp' })).toBeInTheDocument();
  });

  it('renders drawdown chart table mode', () => {
    render(
      <DrawdownChart
        points={[
          { timestamp: '2026-01-01T00:00:00Z', drawdownPct: 0 },
          { timestamp: '2026-01-02T00:00:00Z', drawdownPct: 4.2 },
        ]}
      />
    );

    expect(screen.getByText('Drawdown Curve')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Table' }));
    expect(screen.getByRole('columnheader', { name: 'Drawdown %' })).toBeInTheDocument();
  });
});
