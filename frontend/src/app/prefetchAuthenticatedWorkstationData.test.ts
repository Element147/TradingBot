import { describe, expect, it, vi } from 'vitest';

import { prefetchAuthenticatedWorkstationData } from './prefetchAuthenticatedWorkstationData';

const getBalancePrefetch = vi.fn(() => ({ type: 'account/getBalance' }));
const getPerformancePrefetch = vi.fn(() => ({ type: 'account/getPerformance' }));
const getOpenPositionsPrefetch = vi.fn(() => ({ type: 'account/getOpenPositions' }));
const getRecentTradesPrefetch = vi.fn(() => ({ type: 'account/getRecentTrades' }));
const getBacktestsPrefetch = vi.fn(() => ({ type: 'backtest/getBacktests' }));
const getMarketDataProvidersPrefetch = vi.fn(() => ({ type: 'marketData/getProviders' }));
const getMarketDataJobsPrefetch = vi.fn(() => ({ type: 'marketData/getJobs' }));
const getPaperTradingStatePrefetch = vi.fn(() => ({ type: 'paper/getState' }));
const getPaperOrdersPrefetch = vi.fn(() => ({ type: 'paper/getOrders' }));
const getRiskStatusPrefetch = vi.fn(() => ({ type: 'risk/getStatus' }));
const getStrategiesPrefetch = vi.fn(() => ({ type: 'strategies/getStrategies' }));
const getTradeHistoryPrefetch = vi.fn(() => ({ type: 'trades/getHistory' }));

vi.mock('@/features/account/accountApi', () => ({
  accountApi: {
    util: {
      prefetch: (...args: unknown[]) => {
        const [endpoint] = args;
        if (endpoint === 'getBalance') {
          return getBalancePrefetch(...args);
        }
        if (endpoint === 'getPerformance') {
          return getPerformancePrefetch(...args);
        }
        if (endpoint === 'getOpenPositions') {
          return getOpenPositionsPrefetch(...args);
        }
        return getRecentTradesPrefetch(...args);
      },
    },
  },
}));

vi.mock('@/features/backtest/backtestApi', () => ({
  backtestApi: {
    util: {
      prefetch: (...args: unknown[]) => getBacktestsPrefetch(...args),
    },
  },
}));

vi.mock('@/features/marketData/marketDataApi', () => ({
  marketDataApi: {
    util: {
      prefetch: (...args: unknown[]) => {
        const [, queryArg] = args;
        if (queryArg === undefined) {
          return getMarketDataProvidersPrefetch(...args);
        }
        return getMarketDataJobsPrefetch(...args);
      },
    },
  },
}));

vi.mock('@/features/paperApi', () => ({
  paperApi: {
    util: {
      prefetch: (...args: unknown[]) => {
        const [endpoint] = args;
        return endpoint === 'getPaperTradingState'
          ? getPaperTradingStatePrefetch(...args)
          : getPaperOrdersPrefetch(...args);
      },
    },
  },
}));

vi.mock('@/features/risk/riskApi', () => ({
  riskApi: {
    util: {
      prefetch: (...args: unknown[]) => getRiskStatusPrefetch(...args),
    },
  },
}));

vi.mock('@/features/strategies/strategiesApi', () => ({
  PAPER_STRATEGIES_QUERY: { executionContext: 'paper' },
  strategiesApi: {
    util: {
      prefetch: (...args: unknown[]) => getStrategiesPrefetch(...args),
    },
  },
}));

vi.mock('@/features/trades/tradesApi', () => ({
  tradesApi: {
    util: {
      prefetch: (...args: unknown[]) => getTradeHistoryPrefetch(...args),
    },
  },
}));

describe('prefetchAuthenticatedWorkstationData', () => {
  it('prefetches strategies with the paper execution context cache key', () => {
    const dispatch = vi.fn();

    prefetchAuthenticatedWorkstationData(dispatch);

    expect(getStrategiesPrefetch).toHaveBeenCalledWith(
      'getStrategies',
      { executionContext: 'paper' },
      { force: true }
    );
    expect(dispatch).toHaveBeenCalled();
  });
});
