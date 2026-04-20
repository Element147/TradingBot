import { createApi } from '@reduxjs/toolkit/query/react';

import {
  baseQueryWithEnvironment,
  withExecutionContext,
  type ExecutionContextOverride,
} from '@/services/api';

export interface Strategy {
  id: number;
  name: string;
  type: string;
  status: 'RUNNING' | 'STOPPED' | 'ERROR';
  symbol: string;
  timeframe: string;
  riskPerTrade: number;
  minPositionSize: number;
  maxPositionSize: number;
  profitLoss: number;
  tradeCount: number;
  currentDrawdown: number;
  paperMode: boolean;
  shortSellingEnabled: boolean;
  configVersion: number;
  lastConfigChangedAt: string | null;
}

export interface StrategyConfigHistoryEntry {
  id: number;
  versionNumber: number;
  changeReason: string;
  symbol: string;
  timeframe: string;
  riskPerTrade: number;
  minPositionSize: number;
  maxPositionSize: number;
  status: string;
  paperMode: boolean;
  shortSellingEnabled: boolean;
  changedAt: string;
}

export interface UpdateStrategyConfigPayload {
  strategyId: number;
  symbol: string;
  timeframe: string;
  riskPerTrade: number;
  minPositionSize: number;
  maxPositionSize: number;
  shortSellingEnabled: boolean;
}

interface StrategyQueryOptions {
  executionContext?: ExecutionContextOverride;
}

interface StrategyConfigHistoryQuery extends StrategyQueryOptions {
  strategyId: number;
}

export const PAPER_STRATEGIES_QUERY = { executionContext: 'paper' } as const;

const STRATEGY_QUERY_CACHE_ARGS: Array<StrategyQueryOptions | void> = [
  undefined,
  PAPER_STRATEGIES_QUERY,
  { executionContext: 'forward-test' },
  { executionContext: 'live' },
];

export const strategiesApi = createApi({
  reducerPath: 'strategiesApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['Strategies'],
  keepUnusedDataFor: 300,
  endpoints: (builder) => ({
    getStrategies: builder.query<Strategy[], StrategyQueryOptions | void>({
      query: (arg) =>
        arg?.executionContext
          ? withExecutionContext('/api/strategies', arg.executionContext)
          : '/api/strategies',
      providesTags: ['Strategies'],
    }),
    startStrategy: builder.mutation<{ strategyId: number; status: string }, number>({
      query: (strategyId) => ({
        url: `/api/strategies/${strategyId}/start`,
        method: 'POST',
      }),
      async onQueryStarted(strategyId, { dispatch, queryFulfilled }) {
        const patchResults = STRATEGY_QUERY_CACHE_ARGS.map((queryArg) =>
          dispatch(strategiesApi.util.updateQueryData('getStrategies', queryArg, (draft) => {
            const match = draft.find((strategy) => strategy.id === strategyId);
            if (match) {
              match.status = 'RUNNING';
            }
          }))
        );
        try {
          await queryFulfilled;
        } catch {
          patchResults.forEach((patchResult) => patchResult.undo());
        }
      },
      invalidatesTags: ['Strategies'],
    }),
    stopStrategy: builder.mutation<{ strategyId: number; status: string }, number>({
      query: (strategyId) => ({
        url: `/api/strategies/${strategyId}/stop`,
        method: 'POST',
      }),
      async onQueryStarted(strategyId, { dispatch, queryFulfilled }) {
        const patchResults = STRATEGY_QUERY_CACHE_ARGS.map((queryArg) =>
          dispatch(strategiesApi.util.updateQueryData('getStrategies', queryArg, (draft) => {
            const match = draft.find((strategy) => strategy.id === strategyId);
            if (match) {
              match.status = 'STOPPED';
            }
          }))
        );
        try {
          await queryFulfilled;
        } catch {
          patchResults.forEach((patchResult) => patchResult.undo());
        }
      },
      invalidatesTags: ['Strategies'],
    }),
    updateStrategyConfig: builder.mutation<Strategy, UpdateStrategyConfigPayload>({
      query: ({ strategyId, ...body }) => ({
        url: `/api/strategies/${strategyId}/config`,
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Strategies'],
    }),
    getStrategyConfigHistory: builder.query<
      StrategyConfigHistoryEntry[],
      number | StrategyConfigHistoryQuery | undefined
    >({
      query: (arg) => {
        if (!arg) {
          return '/api/strategies/0/config-history';
        }

        if (typeof arg === 'number') {
          return `/api/strategies/${arg}/config-history`;
        }

        const request = `/api/strategies/${arg.strategyId}/config-history`;
        return arg.executionContext
          ? withExecutionContext(request, arg.executionContext)
          : request;
      },
    }),
  }),
});

export const {
  useGetStrategiesQuery,
  useStartStrategyMutation,
  useStopStrategyMutation,
  useUpdateStrategyConfigMutation,
  useGetStrategyConfigHistoryQuery,
} = strategiesApi;
