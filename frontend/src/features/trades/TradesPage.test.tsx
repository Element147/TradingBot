import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import TradesPage from './TradesPage';

vi.mock('react-redux', async () => {
  const actual = await vi.importActual<typeof import('react-redux')>('react-redux');
  return {
    ...actual,
    useSelector: (selector: (state: { settings: { currency: 'USD' | 'BTC'; timezone: string } }) => unknown) =>
      selector({ settings: { currency: 'USD', timezone: 'UTC' } }),
  };
});

vi.mock('./tradesApi', () => ({
  useGetTradeHistoryQuery: () => ({
    data: {
      items: [
        {
          id: 10,
          pair: 'BTC/USDT',
          entryTime: '2026-02-01T10:00:00',
          entryPrice: 100,
          exitTime: '2026-02-01T14:00:00',
          exitPrice: 110,
          signal: 'BUY',
          positionSide: 'LONG',
          positionSize: 0.5,
          riskAmount: 20,
          pnl: 5,
          feesActual: 1,
          slippageActual: 0.2,
          stopLoss: 90,
          takeProfit: 120,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 50,
    },
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useGetTradeDetailsQuery: () => ({
    data: {
      id: 10,
      pair: 'BTC/USDT',
      entryTime: '2026-02-01T10:00:00',
      entryPrice: 100,
      exitTime: '2026-02-01T14:00:00',
      exitPrice: 110,
      signal: 'BUY',
      positionSide: 'LONG',
      positionSize: 0.5,
      riskAmount: 20,
      pnl: 5,
      feesActual: 1,
      slippageActual: 0.2,
      stopLoss: 90,
      takeProfit: 120,
    },
    isFetching: false,
    isError: false,
  }),
}));

vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('./VirtualizedTradeTable', () => ({
  VirtualizedTradeTable: ({
    rows,
    onRowSelect,
  }: {
    rows: Array<{ id: number; pair: string }>;
    onRowSelect: (trade: { id: number; pair: string }) => void;
  }) => (
    <div>
      {rows.map((row) => (
        <button key={row.id} type="button" onClick={() => onRowSelect(row)}>
          {row.pair}
        </button>
      ))}
    </div>
  ),
}));

describe('TradesPage', { timeout: 15000 }, () => {
  const renderPage = () =>
    render(
      <MemoryRouter>
        <TradesPage />
      </MemoryRouter>
    );

  it('renders trade table and summary', () => {
    renderPage();

    expect(screen.getByText('Review and export')).toBeInTheDocument();
    expect(screen.getByText('Trade review')).toBeInTheDocument();
    expect(screen.getByText('BTC/USDT')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeInTheDocument();
  });

  it('shows detail panel after selecting row', () => {
    renderPage();

    fireEvent.click(screen.getByText('BTC/USDT'));

    expect(screen.getByText('Trade Detail #10')).toBeInTheDocument();
    expect(screen.getByText('Pair: BTC/USDT')).toBeInTheDocument();
  });
});
