import { createApi } from '@reduxjs/toolkit/query/react';

import type { BalanceData, PerformanceMetrics, PerformanceTimeframe } from './accountContract';
import {
  normalizeBalanceData,
  normalizeOpenPositions,
  normalizePerformanceMetrics,
  normalizeRecentTrades,
} from './accountContract';

import {
  baseQueryWithEnvironment,
  withExecutionContext,
  type ExecutionContextOverride,
} from '@/services/api';
import type { Position, Trade } from '@/types/domain.types';

export type { BalanceData, PerformanceMetrics, PerformanceTimeframe } from './accountContract';

interface AccountQueryOptions {
  executionContext?: ExecutionContextOverride;
}

type PerformanceQueryArg =
  | PerformanceTimeframe
  | (AccountQueryOptions & { timeframe?: PerformanceTimeframe });

type RecentTradesQueryArg = number | (AccountQueryOptions & { limit?: number });

/**
 * Account API slice for balance and performance data
 *
 * Features:
 * - Environment-aware queries (test/live mode via X-Environment header)
 * - Cache invalidation tags for data freshness
 * - Polling for live environment (60 second interval)
 * - Automatic refetch on environment switch
 */
export const accountApi = createApi({
  reducerPath: 'accountApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['Balance', 'Performance', 'Positions', 'Trades'],
  keepUnusedDataFor: 300,
  endpoints: (builder) => ({
    /**
     * Get account balance
     *
     * Fetches balance data from the appropriate environment (test or live)
     * based on the X-Environment header set by baseQueryWithEnvironment.
     *
     * In live mode, polling is enabled with 60 second interval.
     */
    getBalance: builder.query<BalanceData, AccountQueryOptions | void>({
      query: (arg) =>
        arg?.executionContext
          ? withExecutionContext('/api/account/balance', arg.executionContext)
          : '/api/account/balance',
      transformResponse: normalizeBalanceData,
      providesTags: ['Balance'],
    }),

    /**
     * Get performance metrics
     *
     * Fetches performance data for the specified timeframe.
     *
     * @param timeframe - Time period for metrics (today, week, month, all)
     */
    getPerformance: builder.query<PerformanceMetrics, PerformanceQueryArg>({
      query: (arg) => {
        const timeframe = typeof arg === 'string' ? arg : (arg?.timeframe ?? 'month');
        const request = {
          url: '/api/account/performance',
          params: { timeframe },
        };

        return typeof arg === 'object' && arg?.executionContext
          ? withExecutionContext(request, arg.executionContext)
          : request;
      },
      transformResponse: normalizePerformanceMetrics,
      providesTags: ['Performance'],
    }),

    /**
     * Get open positions
     *
     * Fetches all currently open positions for the active environment.
     * Updates in real-time via WebSocket events.
     */
    getOpenPositions: builder.query<Position[], AccountQueryOptions | void>({
      query: (arg) =>
        arg?.executionContext
          ? withExecutionContext('/api/positions/open', arg.executionContext)
          : '/api/positions/open',
      transformResponse: normalizeOpenPositions,
      providesTags: ['Positions'],
    }),

    /**
     * Get recent trades
     *
     * Fetches the most recent completed trades.
     *
     * @param limit - Number of trades to fetch (default: 10)
     */
    getRecentTrades: builder.query<Trade[], RecentTradesQueryArg | void>({
      query: (arg) => {
        const limit = typeof arg === 'number' ? arg : (arg?.limit ?? 10);
        const request = {
          url: '/api/trades/recent',
          params: { limit },
        };

        return typeof arg === 'object' && arg?.executionContext
          ? withExecutionContext(request, arg.executionContext)
          : request;
      },
      transformResponse: normalizeRecentTrades,
      providesTags: ['Trades'],
    }),
  }),
});

export const {
  useGetBalanceQuery,
  useGetPerformanceQuery,
  useGetOpenPositionsQuery,
  useGetRecentTradesQuery,
} = accountApi;
