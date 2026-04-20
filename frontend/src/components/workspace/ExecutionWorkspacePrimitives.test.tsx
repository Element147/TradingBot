import { Button, ThemeProvider, createTheme } from '@mui/material';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import {
  ActiveAlgorithmDetailDrawer,
  ExecutionCard,
  ExecutionStatusRail,
  InvestigationLogPanel,
  LiveMetricStrip,
} from './ExecutionWorkspacePrimitives';

const theme = createTheme();

function renderWithTheme(component: React.ReactElement) {
  return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);
}

describe('ExecutionWorkspacePrimitives', () => {
  it('renders execution status rail items and empty state', () => {
    const { rerender } = renderWithTheme(
      <ExecutionStatusRail
        title="Execution posture"
        items={[
          { label: 'Context', value: 'Forward test', detail: 'Observation only', tone: 'info' },
          { label: 'Freshness', value: '12s old', detail: 'Polling fallback', tone: 'warning' },
        ]}
      />
    );

    expect(screen.getByText('Execution posture')).toBeInTheDocument();
    expect(screen.getByText('Forward test')).toBeInTheDocument();
    expect(screen.getByText('12s old')).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={theme}>
        <ExecutionStatusRail title="Execution posture" items={[]} />
      </ThemeProvider>
    );

    expect(screen.getByText('No execution context selected')).toBeInTheDocument();
  });

  it('renders an execution card and supports keyboard-safe selection state', () => {
    const onSelect = vi.fn();

    renderWithTheme(
      <ExecutionCard
        title="BTC Momentum"
        subtitle="Binance testnet"
        metrics={[
          { label: 'PnL', value: '+3.4%', tone: 'success' },
          { label: 'Incidents', value: '0', tone: 'info' },
        ]}
        selected
        onSelect={onSelect}
        ariaLabel="Select BTC Momentum"
      />
    );

    const button = screen.getByRole('button', { name: 'Select BTC Momentum' });
    expect(button).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(button);
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  it('renders investigation log loading, populated, and empty states', () => {
    const { rerender } = renderWithTheme(
      <InvestigationLogPanel title="Investigation log" entries={[]} loading />
    );

    expect(screen.getByText('Loading investigation history...')).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={theme}>
        <InvestigationLogPanel
          title="Investigation log"
          entries={[
            {
              id: 'note-1',
              timestamp: '2026-03-26 10:15',
              title: 'Signal rejected',
              detail: 'Volume confirmation failed on the reclaim candle.',
              tags: ['Volume', 'Rejected'],
              tone: 'warning',
            },
          ]}
        />
      </ThemeProvider>
    );

    expect(screen.getByText('Signal rejected')).toBeInTheDocument();
    expect(screen.getByText('Volume confirmation failed on the reclaim candle.')).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={theme}>
        <InvestigationLogPanel title="Investigation log" entries={[]} />
      </ThemeProvider>
    );

    expect(screen.getByText('No investigation entries yet')).toBeInTheDocument();
  });

  it('keeps dense investigation content readable in narrow layouts', () => {
    renderWithTheme(
      <InvestigationLogPanel
        title="Investigation log"
        entries={[
          {
            id: 'dense-1',
            timestamp: 'Current desk state refreshed at 2026-03-31 15:50:00 UTC',
            title: 'STALE_POSITIONS: 1 paper position(s) have not updated in over 6 hours.',
            detail:
              'Reconcile stale positions, then verify the recovery telemetry returns to HEALTHY before resuming review.',
            tags: ['Paper recovery warning', 'Current desk state'],
            tone: 'warning',
          },
        ]}
      />
    );

    expect(
      screen.getByText(/STALE_POSITIONS: 1 paper position\(s\) have not updated/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Current desk state refreshed/i)).toBeInTheDocument();
    expect(screen.getByText('Paper recovery warning')).toBeInTheDocument();
  });

  it('renders a live metric strip and empty metric fallback', () => {
    const { rerender } = renderWithTheme(
      <LiveMetricStrip
        items={[
          { label: 'Open PnL', value: '+124.33', tone: 'success' },
          { label: 'Incidents', value: '1', tone: 'warning', detail: 'Feed stale warning' },
        ]}
      />
    );

    expect(screen.getByText('Live metric strip')).toBeInTheDocument();
    expect(screen.getByText('+124.33')).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={theme}>
        <LiveMetricStrip items={[]} />
      </ThemeProvider>
    );

    expect(screen.getByText('No live metrics available')).toBeInTheDocument();
  });

  it('renders active algorithm detail drawer content and mobile opener', () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === '(min-width:1536px)' ? false : false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    renderWithTheme(
      <ActiveAlgorithmDetailDrawer
        title="Active algorithm detail"
        description="Inspect selected execution state."
        statusChips={<Button variant="outlined">RUNNING</Button>}
        summary={<div>Current position: long 0.25 BTC</div>}
        sections={[
          {
            id: 'risk',
            title: 'Risk posture',
            content: <div>Max open risk remains below threshold.</div>,
          },
        ]}
      />
    );

    expect(screen.getByText('Active algorithm detail')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open active algorithm detail' })).toBeInTheDocument();
    expect(screen.getByText('Open the detail drawer to inspect selected algorithm state, recent signals, risk, and operator notes without losing the main chart or list context.')).toBeInTheDocument();
  });
});
