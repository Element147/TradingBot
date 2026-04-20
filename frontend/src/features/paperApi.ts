import { createApi } from '@reduxjs/toolkit/query/react';

import {
  baseQueryWithEnvironment,
  withExecutionContext,
  type ExecutionContextOverride,
} from '@/services/api';

export interface PaperTradingState {
  paperMode: boolean;
  cashBalance: number;
  positionCount: number;
  totalOrders: number;
  openOrders: number;
  filledOrders: number;
  cancelledOrders: number;
  lastOrderAt: string | null;
  lastPositionUpdateAt: string | null;
  staleOpenOrderCount: number;
  stalePositionCount: number;
  recoveryStatus: 'HEALTHY' | 'IDLE' | 'ATTENTION';
  recoveryMessage: string;
  incidentSummary: string;
  alerts: Array<{
    severity: 'INFO' | 'WARNING';
    code: string;
    summary: string;
    recommendedAction: string;
  }>;
}

export type PaperOrderSide = 'BUY' | 'SELL' | 'SHORT' | 'COVER';

export type PaperOrderStatus = 'NEW' | 'FILLED' | 'CANCELLED';

export interface PaperOrder {
  id: number;
  symbol: string;
  side: PaperOrderSide;
  status: PaperOrderStatus;
  quantity: number;
  price: number;
  fillPrice: number | null;
  fees: number | null;
  slippage: number | null;
  createdAt: string;
}

export interface PlacePaperOrderPayload {
  symbol: string;
  side: PaperOrderSide;
  quantity: number;
  price: number;
  executeNow: boolean;
}

interface PaperQueryOptions {
  executionContext?: ExecutionContextOverride;
}

export const paperApi = createApi({
  reducerPath: 'paperApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['PaperTrading', 'PaperOrders'],
  keepUnusedDataFor: 300,
  endpoints: (builder) => ({
    getPaperTradingState: builder.query<PaperTradingState, PaperQueryOptions | void>({
      query: (arg) =>
        arg?.executionContext
          ? withExecutionContext('/api/paper/state', arg.executionContext)
          : '/api/paper/state',
      providesTags: ['PaperTrading'],
    }),
    getPaperOrders: builder.query<PaperOrder[], PaperQueryOptions | void>({
      query: (arg) =>
        arg?.executionContext
          ? withExecutionContext('/api/paper/orders', arg.executionContext)
          : '/api/paper/orders',
      providesTags: ['PaperOrders'],
    }),
    placePaperOrder: builder.mutation<PaperOrder, PlacePaperOrderPayload>({
      query: (body) => ({
        url: '/api/paper/orders',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['PaperTrading', 'PaperOrders'],
    }),
    fillPaperOrder: builder.mutation<PaperOrder, number>({
      query: (orderId) => ({
        url: `/api/paper/orders/${orderId}/fill`,
        method: 'POST',
      }),
      invalidatesTags: ['PaperTrading', 'PaperOrders'],
    }),
    cancelPaperOrder: builder.mutation<PaperOrder, number>({
      query: (orderId) => ({
        url: `/api/paper/orders/${orderId}/cancel`,
        method: 'POST',
      }),
      invalidatesTags: ['PaperTrading', 'PaperOrders'],
    }),
  }),
});

export const {
  useGetPaperTradingStateQuery,
  useGetPaperOrdersQuery,
  usePlacePaperOrderMutation,
  useFillPaperOrderMutation,
  useCancelPaperOrderMutation,
} = paperApi;
