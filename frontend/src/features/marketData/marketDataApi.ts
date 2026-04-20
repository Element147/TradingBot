import { createApi } from '@reduxjs/toolkit/query/react';

import { baseQueryWithEnvironment } from '@/services/api';

export interface MarketDataProvider {
  id: string;
  label: string;
  description: string;
  supportedAssetTypes: Array<'STOCK' | 'CRYPTO'>;
  supportedTimeframes: string[];
  apiKeyRequired: boolean;
  apiKeyEnvironmentVariable: string | null;
  apiKeyConfigured: boolean;
  apiKeyConfiguredSource: 'NOT_REQUIRED' | 'DATABASE' | 'ENVIRONMENT' | 'DATABASE_LOCKED' | 'NONE';
  supportsAdjusted: boolean;
  supportsRegularSessionOnly: boolean;
  symbolExamples: string[];
  docsUrl: string;
  signupUrl: string;
  accountNotes: string;
}

export interface MarketDataProviderCredentialSetting {
  providerId: string;
  providerLabel: string;
  apiKeyEnvironmentVariable: string | null;
  apiKeyRequired: boolean;
  hasStoredCredential: boolean;
  hasEnvironmentCredential: boolean;
  effectiveCredentialConfigured: boolean;
  credentialSource: 'DATABASE' | 'ENVIRONMENT' | 'DATABASE_LOCKED' | 'NONE' | 'NOT_REQUIRED';
  storageEncryptionConfigured: boolean;
  note: string | null;
  updatedAt: string | null;
  docsUrl: string;
  signupUrl: string;
  accountNotes: string;
}

export interface MarketDataImportJob {
  id: number;
  providerId: string;
  providerLabel: string;
  assetType: 'STOCK' | 'CRYPTO';
  datasetName: string;
  symbolsCsv: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  adjusted: boolean;
  regularSessionOnly: boolean;
  status: 'QUEUED' | 'RUNNING' | 'WAITING_RETRY' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  statusMessage: string;
  nextRetryAt: string | null;
  currentSymbolIndex: number;
  totalSymbols: number;
  currentSymbol: string | null;
  importedRowCount: number;
  datasetId: number | null;
  datasetReady: boolean;
  currentChunkStart: string | null;
  attemptCount: number;
  retryCount?: number;
  maxRetryCount?: number;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  asyncMonitor?: {
    state: 'QUEUED' | 'RUNNING' | 'WAITING_RETRY' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
    attemptCount: number;
    maxAttempts: number | null;
    nextRetryAt: string | null;
    retryEligible: boolean;
    timedOut: boolean;
    timeoutThresholdSeconds: number | null;
  };
}

export interface CreateMarketDataJobPayload {
  providerId: string;
  assetType: 'STOCK' | 'CRYPTO';
  symbols: string[];
  timeframe: string;
  startDate: string;
  endDate: string;
  datasetName?: string;
  adjusted?: boolean;
  regularSessionOnly?: boolean;
}

export interface SaveMarketDataProviderCredentialPayload {
  providerId: string;
  apiKey?: string;
  note?: string;
}

export const marketDataApi = createApi({
  reducerPath: 'marketDataApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['MarketDataJobs', 'MarketDataCredentials'],
  endpoints: (builder) => ({
    getMarketDataProviders: builder.query<MarketDataProvider[], void>({
      query: () => '/api/market-data/providers',
      providesTags: ['MarketDataCredentials'],
    }),
    getMarketDataProviderCredentials: builder.query<MarketDataProviderCredentialSetting[], void>({
      query: () => '/api/market-data/provider-credentials',
      providesTags: ['MarketDataCredentials'],
    }),
    getMarketDataJobs: builder.query<MarketDataImportJob[], void>({
      query: () => '/api/market-data/jobs',
      providesTags: ['MarketDataJobs'],
    }),
    createMarketDataJob: builder.mutation<MarketDataImportJob, CreateMarketDataJobPayload>({
      query: (body) => ({
        url: '/api/market-data/jobs',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['MarketDataJobs'],
    }),
    saveMarketDataProviderCredential: builder.mutation<
      MarketDataProviderCredentialSetting,
      SaveMarketDataProviderCredentialPayload
    >({
      query: ({ providerId, ...body }) => ({
        url: `/api/market-data/provider-credentials/${providerId}`,
        method: 'POST',
        body,
      }),
      invalidatesTags: ['MarketDataCredentials'],
    }),
    deleteMarketDataProviderCredential: builder.mutation<void, string>({
      query: (providerId) => ({
        url: `/api/market-data/provider-credentials/${providerId}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['MarketDataCredentials'],
    }),
    retryMarketDataJob: builder.mutation<MarketDataImportJob, number>({
      query: (jobId) => ({
        url: `/api/market-data/jobs/${jobId}/retry`,
        method: 'POST',
      }),
      invalidatesTags: ['MarketDataJobs'],
    }),
    cancelMarketDataJob: builder.mutation<MarketDataImportJob, number>({
      query: (jobId) => ({
        url: `/api/market-data/jobs/${jobId}/cancel`,
        method: 'POST',
      }),
      invalidatesTags: ['MarketDataJobs'],
    }),
  }),
});

export const {
  useGetMarketDataProvidersQuery,
  useGetMarketDataProviderCredentialsQuery,
  useGetMarketDataJobsQuery,
  useCreateMarketDataJobMutation,
  useSaveMarketDataProviderCredentialMutation,
  useDeleteMarketDataProviderCredentialMutation,
  useRetryMarketDataJobMutation,
  useCancelMarketDataJobMutation,
} = marketDataApi;
