import { createApi } from '@reduxjs/toolkit/query/react';

import {
  normalizeBacktestAlgorithms,
  normalizeBacktestComparisonResponse,
  normalizeBacktestDataset,
  normalizeBacktestDatasetRetentionReport,
  normalizeBacktestDatasets,
  normalizeBacktestDetails,
  normalizeBacktestEquityCurve,
  normalizeBacktestExperimentSummaries,
  normalizeBacktestHistory,
  normalizeBacktestRunSubmission,
  normalizeBacktestSummary,
  normalizeBacktestTelemetryResponse,
  normalizeBacktestTradeSeries,
  toRunBacktestRequest,
} from './backtestContract';
import type {
  BacktestAlgorithm,
  BacktestComparisonResponse,
  BacktestDataset,
  BacktestDatasetRetentionReport,
  BacktestDetails,
  BacktestEquityPoint,
  BacktestExperimentSummary,
  BacktestHistoryQuery,
  BacktestHistoryResult,
  BacktestRunSubmission,
  BacktestSummary,
  BacktestTelemetryQueryResponse,
  BacktestTradeSeriesItem,
  RunBacktestPayload,
} from './backtestTypes';

import { baseQueryWithEnvironment } from '@/services/api';

export type {
  BacktestAlgorithm,
  BacktestComparisonItem,
  BacktestComparisonResponse,
  BacktestDataset,
  BacktestDatasetRetentionReport,
  BacktestDatasetRetentionStatus,
  BacktestDetails,
  BacktestEquityPoint,
  BacktestExecutionStage,
  BacktestExecutionStatus,
  BacktestExperimentSummary,
  BacktestHistoryItem,
  BacktestHistoryQuery,
  BacktestHistoryResult,
  BacktestHistorySortField,
  BacktestRunSubmission,
  BacktestSummary,
  BacktestSelectionMode,
  BacktestIndicatorPane,
  BacktestIndicatorSeries,
  BacktestRegime,
  BacktestSymbolTelemetry,
  BacktestTelemetryQueryResponse,
  BacktestTelemetryPoint,
  BacktestTradeSeriesItem,
  BacktestTradeSide,
  BacktestValidationStatus,
  RunBacktestPayload,
} from './backtestTypes';

export const backtestApi = createApi({
  reducerPath: 'backtestApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['Backtests', 'BacktestDatasets'],
  keepUnusedDataFor: 600,
  endpoints: (builder) => ({
    getBacktests: builder.query<BacktestHistoryResult, BacktestHistoryQuery | void>({
      query: (params) => ({
        url: '/api/backtests',
        params: params ?? undefined,
      }),
      transformResponse: (response, _meta, arg) =>
        normalizeBacktestHistory(response as Parameters<typeof normalizeBacktestHistory>[0], {
          page: arg?.page ?? 1,
          pageSize: arg?.pageSize ?? 25,
        }),
      providesTags: ['Backtests'],
    }),
    getBacktestExperimentSummaries: builder.query<BacktestExperimentSummary[], void>({
      query: () => '/api/backtests/experiments',
      transformResponse: normalizeBacktestExperimentSummaries,
      providesTags: ['Backtests'],
    }),
    getBacktestDetails: builder.query<BacktestDetails, number>({
      query: (id) => `/api/backtests/${id}`,
      transformResponse: normalizeBacktestDetails,
      providesTags: (_result, _error, id) => [{ type: 'Backtests', id }],
    }),
    getBacktestEquityCurve: builder.query<BacktestEquityPoint[], number>({
      query: (id) => `/api/backtests/${id}/equity`,
      transformResponse: normalizeBacktestEquityCurve,
      providesTags: (_result, _error, id) => [{ type: 'Backtests', id }],
    }),
    getBacktestTradeSeries: builder.query<BacktestTradeSeriesItem[], number>({
      query: (id) => `/api/backtests/${id}/trades`,
      transformResponse: normalizeBacktestTradeSeries,
      providesTags: (_result, _error, id) => [{ type: 'Backtests', id }],
    }),
    getBacktestTelemetry: builder.query<
      BacktestTelemetryQueryResponse,
      { id: number; symbol?: string | null }
    >({
      query: ({ id, symbol }) =>
        symbol
          ? `/api/backtests/${id}/telemetry?symbol=${encodeURIComponent(symbol)}`
          : `/api/backtests/${id}/telemetry`,
      transformResponse: normalizeBacktestTelemetryResponse,
      providesTags: (_result, _error, { id }) => [{ type: 'Backtests', id }],
    }),
    getBacktestSummary: builder.query<BacktestSummary, number>({
      query: (id) => `/api/backtests/${id}/summary`,
      transformResponse: normalizeBacktestSummary,
      providesTags: (_result, _error, id) => [{ type: 'Backtests', id }],
    }),
    getBacktestAlgorithms: builder.query<BacktestAlgorithm[], void>({
      query: () => '/api/backtests/algorithms',
      transformResponse: normalizeBacktestAlgorithms,
    }),
    getBacktestDatasets: builder.query<BacktestDataset[], void>({
      query: () => '/api/backtests/datasets',
      transformResponse: normalizeBacktestDatasets,
      providesTags: ['BacktestDatasets'],
    }),
    getBacktestDatasetRetentionReport: builder.query<BacktestDatasetRetentionReport, void>({
      query: () => '/api/backtests/datasets/retention-report',
      transformResponse: normalizeBacktestDatasetRetentionReport,
      providesTags: ['BacktestDatasets'],
    }),
    uploadBacktestDataset: builder.mutation<BacktestDataset, { file: File; name?: string }>({
      query: ({ file, name }) => {
        const formData = new FormData();
        formData.append('file', file);
        if (name?.trim()) {
          formData.append('name', name.trim());
        }

        return {
          url: '/api/backtests/datasets/upload',
          method: 'POST',
          body: formData,
        };
      },
      transformResponse: normalizeBacktestDataset,
      invalidatesTags: ['BacktestDatasets'],
    }),
    archiveBacktestDataset: builder.mutation<
      BacktestDataset,
      { datasetId: number; reason?: string }
    >({
      query: ({ datasetId, reason }) => ({
        url: `/api/backtests/datasets/${datasetId}/archive`,
        method: 'POST',
        body: reason?.trim() ? { reason: reason.trim() } : {},
      }),
      transformResponse: normalizeBacktestDataset,
      invalidatesTags: ['BacktestDatasets'],
    }),
    restoreBacktestDataset: builder.mutation<BacktestDataset, number>({
      query: (datasetId) => ({
        url: `/api/backtests/datasets/${datasetId}/restore`,
        method: 'POST',
      }),
      transformResponse: normalizeBacktestDataset,
      invalidatesTags: ['BacktestDatasets'],
    }),
    runBacktest: builder.mutation<BacktestRunSubmission, RunBacktestPayload>({
      query: (body) => ({
        url: '/api/backtests/run',
        method: 'POST',
        body: toRunBacktestRequest(body),
      }),
      transformResponse: normalizeBacktestRunSubmission,
      invalidatesTags: ['Backtests'],
    }),
    replayBacktest: builder.mutation<BacktestRunSubmission, number>({
      query: (backtestId) => ({
        url: `/api/backtests/${backtestId}/replay`,
        method: 'POST',
      }),
      transformResponse: normalizeBacktestRunSubmission,
      invalidatesTags: ['Backtests'],
    }),
    deleteBacktest: builder.mutation<void, number>({
      query: (backtestId) => ({
        url: `/api/backtests/${backtestId}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Backtests'],
    }),
    compareBacktests: builder.query<BacktestComparisonResponse, number[]>({
      query: (ids) => {
        const params = ids.map((id) => `ids=${encodeURIComponent(String(id))}`).join('&');
        return `/api/backtests/compare?${params}`;
      },
      transformResponse: normalizeBacktestComparisonResponse,
    }),
  }),
});

export const {
  useGetBacktestsQuery,
  useGetBacktestExperimentSummariesQuery,
  useGetBacktestDetailsQuery,
  useGetBacktestEquityCurveQuery,
  useGetBacktestSummaryQuery,
  useGetBacktestTelemetryQuery,
  useGetBacktestTradeSeriesQuery,
  useGetBacktestAlgorithmsQuery,
  useGetBacktestDatasetsQuery,
  useGetBacktestDatasetRetentionReportQuery,
  useUploadBacktestDatasetMutation,
  useArchiveBacktestDatasetMutation,
  useRestoreBacktestDatasetMutation,
  useRunBacktestMutation,
  useReplayBacktestMutation,
  useDeleteBacktestMutation,
  useCompareBacktestsQuery,
  useLazyCompareBacktestsQuery,
} = backtestApi;
