import { createApi } from '@reduxjs/toolkit/query/react';

import { baseQueryWithEnvironment } from '@/services/api';

export interface RiskStatus {
  currentDrawdown: number;
  maxDrawdownLimit: number;
  dailyLoss: number;
  dailyLossLimit: number;
  openRiskExposure: number;
  positionCorrelation: number;
  circuitBreakerActive: boolean;
  circuitBreakerReason: string;
}

export interface RiskConfig {
  maxRiskPerTrade: number;
  maxDailyLossLimit: number;
  maxDrawdownLimit: number;
  maxOpenPositions: number;
  correlationLimit: number;
  circuitBreakerActive: boolean;
  circuitBreakerReason: string;
}

export interface RiskAlert {
  id: number;
  type: string;
  severity: string;
  message: string;
  actionTaken: string;
  timestamp: string;
}

export interface OverridePayload {
  confirmationCode: string;
  reason: string;
}

export const riskApi = createApi({
  reducerPath: 'riskApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['Risk'],
  keepUnusedDataFor: 300,
  endpoints: (builder) => ({
    getRiskStatus: builder.query<RiskStatus, void>({
      query: () => '/api/risk/status',
      providesTags: ['Risk'],
    }),
    getRiskConfig: builder.query<RiskConfig, void>({
      query: () => '/api/risk/config',
      providesTags: ['Risk'],
    }),
    getCircuitBreakers: builder.query<RiskConfig[], void>({
      query: () => '/api/risk/circuit-breakers',
      providesTags: ['Risk'],
    }),
    getRiskAlerts: builder.query<RiskAlert[], void>({
      query: () => '/api/risk/alerts',
      providesTags: ['Risk'],
    }),
    updateRiskConfig: builder.mutation<RiskConfig, Omit<RiskConfig, 'circuitBreakerActive' | 'circuitBreakerReason'>>({
      query: (body) => ({
        url: '/api/risk/config',
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Risk'],
    }),
    overrideCircuitBreaker: builder.mutation<RiskConfig, OverridePayload>({
      query: (body) => ({
        url: '/api/risk/circuit-breaker/override',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Risk'],
    }),
  }),
});

export const {
  useGetRiskStatusQuery,
  useGetRiskConfigQuery,
  useGetCircuitBreakersQuery,
  useGetRiskAlertsQuery,
  useUpdateRiskConfigMutation,
  useOverrideCircuitBreakerMutation,
} = riskApi;
