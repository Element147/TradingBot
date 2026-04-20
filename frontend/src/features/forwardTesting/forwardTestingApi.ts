import { createApi } from '@reduxjs/toolkit/query/react';

import { baseQueryWithEnvironment, withExecutionContext } from '@/services/api';

export interface ForwardTestingStatus {
  accountValue: string;
  pnl: string;
  pnlPercent: string;
  sharpeRatio: string;
  maxDrawdown: string;
  maxDrawdownPercent: string;
  openPositions: number;
  totalTrades: number;
  winRate: string;
  profitFactor: string;
  status: string;
}

export const forwardTestingApi = createApi({
  reducerPath: 'forwardTestingApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['ForwardTestingStatus'],
  keepUnusedDataFor: 60,
  endpoints: (builder) => ({
    getForwardTestingStatus: builder.query<ForwardTestingStatus, { accountId?: number } | void>({
      query: (arg) =>
        withExecutionContext(
          {
            url: '/api/strategy/status',
            params: arg?.accountId ? { accountId: arg.accountId } : undefined,
          },
          'forward-test'
        ),
      providesTags: ['ForwardTestingStatus'],
    }),
  }),
});

export const { useGetForwardTestingStatusQuery } = forwardTestingApi;
