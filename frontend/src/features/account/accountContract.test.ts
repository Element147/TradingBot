import { describe, expect, it } from 'vitest';

import {
  normalizeBalanceData,
  normalizeOpenPositions,
  normalizePerformanceMetrics,
  normalizeRecentTrades,
} from './accountContract';

describe('accountContract', () => {
  it('parses balance and performance responses from generated transport types', () => {
    expect(
      normalizeBalanceData({
        total: '10000.00',
        available: '8500.00',
        locked: '1500.00',
        assets: [
          {
            symbol: 'USD',
            amount: '8500.00',
            valueUSD: '8500.00',
          },
        ],
        lastSync: '2026-03-19T12:00:00Z',
      })
    ).toEqual({
      total: '10000.00',
      available: '8500.00',
      locked: '1500.00',
      assets: [
        {
          symbol: 'USD',
          amount: '8500.00',
          valueUSD: '8500.00',
        },
      ],
      lastSync: '2026-03-19T12:00:00Z',
    });

    expect(
      normalizePerformanceMetrics({
        totalProfitLoss: '125.00',
        profitLossPercentage: '1.25',
        winRate: '58.3',
        tradeCount: 6,
        cashRatio: '85.0',
      })
    ).toEqual({
      totalProfitLoss: '125.00',
      profitLossPercentage: '1.25',
      winRate: '58.3',
      tradeCount: 6,
      cashRatio: '85.0',
    });
  });

  it('normalizes open positions with safe defaults', () => {
    expect(
      normalizeOpenPositions([
        {
          id: 5,
          symbol: 'BTCUSD',
          side: 'SHORT',
          entryPrice: '50000',
          entryTime: '2026-03-19T09:00:00Z',
          unrealizedPnL: '120.00',
          unrealizedPnLPercentage: '2.4',
        },
      ])
    ).toEqual([
      {
        id: '5',
        strategyId: 'unknown',
        strategyName: 'N/A',
        symbol: 'BTCUSD',
        side: 'SHORT',
        entryPrice: '50000',
        currentPrice: '50000',
        quantity: '0',
        entryTime: '2026-03-19T09:00:00Z',
        unrealizedPnL: '120.00',
        unrealizedPnLPercentage: '2.4',
        status: 'OPEN',
      },
    ]);
  });

  it('normalizes recent trades with fallback values for optional transport fields', () => {
    expect(
      normalizeRecentTrades([
        {
          id: 11,
          symbol: 'ETHUSD',
          side: 'LONG',
          entryPrice: '2400',
          exitPrice: '2450',
          entryTime: '2026-03-19T08:00:00Z',
          exitTime: '2026-03-19T10:00:00Z',
          profitLoss: '50.00',
          profitLossPercentage: '2.08',
        },
      ])
    ).toEqual([
      {
        id: '11',
        strategyId: 'unknown',
        strategyName: 'N/A',
        symbol: 'ETHUSD',
        side: 'LONG',
        entryPrice: '2400',
        exitPrice: '2450',
        quantity: '0',
        entryTime: '2026-03-19T08:00:00Z',
        exitTime: '2026-03-19T10:00:00Z',
        duration: 'N/A',
        profitLoss: '50.00',
        profitLossPercentage: '2.08',
        fees: '0',
        slippage: '0',
        status: 'CLOSED',
      },
    ]);
  });
});
