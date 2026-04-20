import type { AppDispatch } from './store';

import { accountApi } from '@/features/account/accountApi';
import { backtestApi } from '@/features/backtest/backtestApi';
import { marketDataApi } from '@/features/marketData/marketDataApi';
import { paperApi } from '@/features/paperApi';
import { riskApi } from '@/features/risk/riskApi';
import {
  PAPER_STRATEGIES_QUERY,
  strategiesApi,
} from '@/features/strategies/strategiesApi';
import { tradesApi } from '@/features/trades/tradesApi';

// Force the first authenticated fetch so any unauthenticated 403 cache is replaced immediately.
const AUTHENTICATED_PREFETCH_OPTIONS = { force: true } as const;

export const prefetchAuthenticatedWorkstationData = (dispatch: AppDispatch) => {
  dispatch(
    accountApi.util.prefetch('getBalance', undefined, AUTHENTICATED_PREFETCH_OPTIONS)
  );
  dispatch(
    accountApi.util.prefetch(
      'getPerformance',
      'today',
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    accountApi.util.prefetch(
      'getOpenPositions',
      undefined,
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    accountApi.util.prefetch('getRecentTrades', 10, AUTHENTICATED_PREFETCH_OPTIONS)
  );
  dispatch(
    paperApi.util.prefetch(
      'getPaperTradingState',
      undefined,
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    paperApi.util.prefetch('getPaperOrders', undefined, AUTHENTICATED_PREFETCH_OPTIONS)
  );
  dispatch(
    strategiesApi.util.prefetch(
      'getStrategies',
      PAPER_STRATEGIES_QUERY,
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    tradesApi.util.prefetch(
      'getTradeHistory',
      { limit: 200 },
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    backtestApi.util.prefetch(
      'getBacktests',
      undefined,
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    marketDataApi.util.prefetch(
      'getMarketDataProviders',
      undefined,
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    marketDataApi.util.prefetch(
      'getMarketDataJobs',
      undefined,
      AUTHENTICATED_PREFETCH_OPTIONS
    )
  );
  dispatch(
    riskApi.util.prefetch('getRiskStatus', undefined, AUTHENTICATED_PREFETCH_OPTIONS)
  );
};
