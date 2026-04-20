import { createApi } from '@reduxjs/toolkit/query/react';

import { normalizeBalanceData, normalizeRecentTrades } from '@/features/account/accountContract';
import { baseQueryWithEnvironment, withEnvironmentMode } from '@/services/api';

export interface ExchangeBalanceResponse {
  total: string;
  available: string;
  locked: string;
  assets: Array<{
    symbol: string;
    amount: string;
    valueUSD: string;
  }>;
  lastSync: string;
  exchange?: string;
}

export interface ExchangeOrder {
  id: string | number;
  symbol: string;
  side: string;
  entryPrice: string;
  quantity: string;
  status: string;
}

export interface ExchangeConnectionStatus {
  connected: boolean;
  exchange: string;
  lastSync: string;
  rateLimitUsage: string;
  error?: string;
}

export interface ExchangeConnectionProfile {
  id: string;
  name: string;
  exchange: string;
  apiKey: string;
  apiSecret: string;
  testnet: boolean;
  active: boolean;
  updatedAt?: string | null;
}

export interface ExchangeConnectionsResponse {
  connections: ExchangeConnectionProfile[];
  activeConnectionId: string | null;
}

export interface ExchangeConnectionProfileRequest {
  name: string;
  exchange: string;
  apiKey: string;
  apiSecret: string;
  testnet: boolean;
}

export interface ExchangeConnectionTestRequest {
  exchange?: string;
  apiKey?: string;
  apiSecret?: string;
  testnet?: boolean;
}

export interface SystemInfo {
  applicationVersion: string;
  lastDeploymentDate: string;
  databaseStatus: string;
}

export interface OperatorAuditEvent {
  id: number;
  actor: string;
  action: string;
  environment: string;
  targetType: string;
  targetId: string | null;
  outcome: string;
  details: string | null;
  createdAt: string;
}

export interface OperatorAuditSummary {
  visibleEventCount: number;
  totalMatchingEvents: number;
  successCount: number;
  failedCount: number;
  uniqueActors: number;
  uniqueActions: number;
  testEventCount: number;
  paperEventCount: number;
  liveEventCount: number;
  latestEventAt: string | null;
}

export interface OperatorAuditEventListResponse {
  summary: OperatorAuditSummary;
  events: OperatorAuditEvent[];
}

export interface OperatorAuditQuery {
  limit?: number;
  environment?: string;
  outcome?: string;
  targetType?: string;
  search?: string;
}

const asText = (value: unknown, fallback = ''): string =>
  typeof value === 'string' || typeof value === 'number' ? String(value) : fallback;

export const exchangeApi = createApi({
  reducerPath: 'exchangeApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['Exchange', 'ExchangeConnections', 'System', 'Audit'],
  keepUnusedDataFor: 300,
  endpoints: (builder) => ({
    getExchangeBalance: builder.query<ExchangeBalanceResponse, void>({
      query: () => withEnvironmentMode('/api/account/balance', 'live'),
      transformResponse: (response: Parameters<typeof normalizeBalanceData>[0]) => ({
        ...normalizeBalanceData(response),
        exchange: 'Live Exchange',
      }),
      providesTags: ['Exchange'],
    }),
    getExchangeOrders: builder.query<ExchangeOrder[], void>({
      query: () =>
        withEnvironmentMode(
          {
        url: '/api/trades/recent',
        params: { limit: 20 },
          },
          'live'
        ),
      transformResponse: (response: Parameters<typeof normalizeRecentTrades>[0]) =>
        normalizeRecentTrades(response).map((trade) => ({
          id: asText(trade.id),
          symbol: trade.symbol,
          side: trade.side,
          entryPrice: trade.entryPrice,
          quantity: trade.quantity,
          status: trade.status,
        })),
      providesTags: ['Exchange'],
    }),
    testExchangeConnection: builder.mutation<ExchangeConnectionStatus, ExchangeConnectionTestRequest | void>({
      query: (body) => ({
        url: '/api/system/test-connection',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Exchange'],
    }),
    getExchangeConnectionStatus: builder.query<ExchangeConnectionStatus, void>({
      query: () => '/api/exchange/connection-status',
      providesTags: ['Exchange'],
    }),
    getSavedExchangeConnections: builder.query<ExchangeConnectionsResponse, void>({
      query: () => '/api/exchange/connections',
      transformResponse: (response: ExchangeConnectionsResponse) => ({
        activeConnectionId: response.activeConnectionId ?? null,
        connections: response.connections.map((connection) => ({
          ...connection,
          exchange: asText(connection.exchange).toLowerCase(),
          apiKey: asText(connection.apiKey),
          apiSecret: asText(connection.apiSecret),
          active: Boolean(connection.active),
          testnet: Boolean(connection.testnet),
        })),
      }),
      providesTags: ['ExchangeConnections'],
    }),
    createSavedExchangeConnection: builder.mutation<
      ExchangeConnectionProfile,
      ExchangeConnectionProfileRequest
    >({
      query: (body) => ({
        url: '/api/exchange/connections',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Exchange', 'ExchangeConnections'],
    }),
    updateSavedExchangeConnection: builder.mutation<
      ExchangeConnectionProfile,
      { id: string; body: ExchangeConnectionProfileRequest }
    >({
      query: ({ id, body }) => ({
        url: `/api/exchange/connections/${id}`,
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Exchange', 'ExchangeConnections'],
    }),
    activateSavedExchangeConnection: builder.mutation<ExchangeConnectionProfile, string>({
      query: (id) => ({
        url: `/api/exchange/connections/${id}/activate`,
        method: 'POST',
      }),
      invalidatesTags: ['Exchange', 'ExchangeConnections'],
    }),
    deleteSavedExchangeConnection: builder.mutation<void, string>({
      query: (id) => ({
        url: `/api/exchange/connections/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Exchange', 'ExchangeConnections'],
    }),
    getSystemInfo: builder.query<SystemInfo, void>({
      query: () => '/api/system/info',
      providesTags: ['System'],
    }),
    triggerBackup: builder.mutation<{ path: string; size: string }, void>({
      query: () => ({
        url: '/api/system/backup',
        method: 'POST',
      }),
      invalidatesTags: ['System'],
    }),
    getAuditEvents: builder.query<OperatorAuditEventListResponse, OperatorAuditQuery | void>({
      query: (queryArg) => ({
        url: '/api/system/audit-events',
        params: queryArg
          ? {
              limit: queryArg.limit,
              environment: queryArg.environment || undefined,
              outcome: queryArg.outcome || undefined,
              targetType: queryArg.targetType || undefined,
              search: queryArg.search || undefined,
            }
          : undefined,
      }),
      providesTags: ['Audit'],
    }),
  }),
});

export const {
  useGetExchangeBalanceQuery,
  useGetExchangeOrdersQuery,
  useTestExchangeConnectionMutation,
  useGetExchangeConnectionStatusQuery,
  useGetSavedExchangeConnectionsQuery,
  useCreateSavedExchangeConnectionMutation,
  useUpdateSavedExchangeConnectionMutation,
  useActivateSavedExchangeConnectionMutation,
  useDeleteSavedExchangeConnectionMutation,
  useGetSystemInfoQuery,
  useTriggerBackupMutation,
  useGetAuditEventsQuery,
} = exchangeApi;
