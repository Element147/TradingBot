import { z } from 'zod';

import type {
  BacktestAlgorithm,
  BacktestComparisonResponse,
  BacktestDataset,
  BacktestDatasetRetentionReport,
  BacktestDetails,
  BacktestEquityPoint,
  BacktestExperimentSummary,
  BacktestHistoryResult,
  BacktestSummary,
  BacktestTelemetryQueryResponse,
  BacktestTradeSeriesItem,
  BacktestRunSubmission,
  RunBacktestPayload,
} from './backtestTypes';

import type { components } from '@/generated/openapi';

type RawBacktestAlgorithm = components['schemas']['BacktestAlgorithmResponse'];
type RawBacktestComparisonResponse = components['schemas']['BacktestComparisonResponse'];
type RawBacktestDataset = components['schemas']['BacktestDatasetResponse'];
type RawBacktestDatasetRetentionReport =
  components['schemas']['BacktestDatasetRetentionReportResponse'];
type RawBacktestDetails = components['schemas']['BacktestDetailsResponse'] & {
  availableTelemetrySymbols?: string[];
  strategyMetrics?: RawBacktestStrategyMetric[];
  asyncMonitor?: RawAsyncTaskMonitor;
};
type RawBacktestTelemetryQueryResponse = components['schemas']['BacktestTelemetryQueryResponse'] & {
  telemetry: RawBacktestSymbolTelemetry;
};
type RawBacktestEquityPoint = components['schemas']['BacktestEquityPointResponse'];
type RawBacktestTradeSeriesItem = components['schemas']['BacktestTradeSeriesItemResponse'];
type RawBacktestExperimentSummary = components['schemas']['BacktestExperimentSummaryResponse'];
type RawBacktestHistoryItem = components['schemas']['BacktestHistoryItemResponse'] & {
  asyncMonitor?: RawAsyncTaskMonitor;
};
type RawBacktestHistoryPageResponse = {
  items?: RawBacktestHistoryItem[];
  total?: number;
  page?: number;
  pageSize?: number;
};
type RawBacktestSummary = components['schemas']['BacktestSummaryResponse'] & {
  strategyMetrics?: RawBacktestStrategyMetric[];
  asyncMonitor?: RawAsyncTaskMonitor;
};
type RawBacktestRunRequest = components['schemas']['RunBacktestRequest'];
type RawBacktestRunSubmission = components['schemas']['BacktestRunResponse'] & {
  asyncMonitor?: RawAsyncTaskMonitor;
};
type RawAsyncTaskMonitor = {
  state: 'QUEUED' | 'RUNNING' | 'WAITING_RETRY' | 'FAILED' | 'COMPLETED' | 'CANCELLED';
  attemptCount: number;
  maxAttempts: number | null;
  nextRetryAt: string | null;
  retryEligible: boolean;
  timedOut: boolean;
  timeoutThresholdSeconds: number | null;
};
type RawBacktestSymbolTelemetry = {
  symbol: string;
  points: RawBacktestTelemetryPoint[];
  actions: RawBacktestActionMarker[];
  indicators: RawBacktestIndicatorSeries[];
};
type RawBacktestTelemetryPoint = {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  exposurePct: number;
  regime: 'WARMUP' | 'RANGE' | 'TREND_UP' | 'TREND_DOWN';
};
type RawBacktestActionMarker = {
  timestamp: string;
  action: 'BUY' | 'SELL' | 'SHORT' | 'COVER';
  price: number;
  label: string;
};
type RawBacktestIndicatorSeries = {
  key: string;
  label: string;
  pane: 'PRICE' | 'OSCILLATOR';
  points: RawBacktestIndicatorPoint[];
};
type RawBacktestIndicatorPoint = {
  timestamp: string;
  value: number | null;
};
type RawBacktestStrategyMetric = {
  key: string;
  label: string;
  value: number;
  displayValue: string;
  description: string;
};

const selectionModeSchema = z.enum(['SINGLE_SYMBOL', 'DATASET_UNIVERSE']);
const executionStatusSchema = z.enum(['PENDING', 'RUNNING', 'COMPLETED', 'FAILED']);
const validationStatusSchema = z.enum(['PENDING', 'PASSED', 'FAILED', 'PRODUCTION_READY']);
const executionStageSchema = z.enum([
  'QUEUED',
  'VALIDATING_REQUEST',
  'LOADING_DATASET',
  'FILTERING_CANDLES',
  'SIMULATING',
  'PERSISTING_RESULTS',
  'COMPLETED',
  'FAILED',
]);
const asyncTaskStateSchema = z.enum([
  'QUEUED',
  'RUNNING',
  'WAITING_RETRY',
  'FAILED',
  'COMPLETED',
  'CANCELLED',
]);
const retentionStatusSchema = z.enum([
  'ACTIVE',
  'ACTIVE_DUPLICATE_RETAINED',
  'ACTIVE_STALE_RETAINED',
  'ARCHIVE_CANDIDATE_DUPLICATE',
  'ARCHIVE_CANDIDATE_UNUSED',
  'ARCHIVED',
]);
const tradeSideSchema = z.enum(['LONG', 'SHORT']);
const actionTypeSchema = z.enum(['BUY', 'SELL', 'SHORT', 'COVER']);
const indicatorPaneSchema = z.enum(['PRICE', 'OSCILLATOR']);
const regimeSchema = z.enum(['WARMUP', 'RANGE', 'TREND_UP', 'TREND_DOWN']);

const asyncTaskMonitorSchema = z.object({
  state: asyncTaskStateSchema,
  attemptCount: z.number().int(),
  maxAttempts: z.number().int().nullable(),
  nextRetryAt: z.string().nullable(),
  retryEligible: z.boolean(),
  timedOut: z.boolean(),
  timeoutThresholdSeconds: z.number().int().nullable(),
});

const backtestAlgorithmSchema = z.object({
  id: z.string().min(1),
  label: z.string().min(1),
  description: z.string().min(1),
  selectionMode: selectionModeSchema,
});

const backtestDatasetSchema = z.object({
  id: z.number().int(),
  name: z.string().min(1),
  originalFilename: z.string().min(1),
  rowCount: z.number().int(),
  symbolsCsv: z.string(),
  dataStart: z.string().min(1),
  dataEnd: z.string().min(1),
  uploadedAt: z.string().min(1),
  checksumSha256: z.string().min(1),
  schemaVersion: z.string().min(1),
  archived: z.boolean(),
  archivedAt: z.string().nullable(),
  archiveReason: z.string().nullable(),
  usageCount: z.number().int(),
  lastUsedAt: z.string().nullable(),
  usedByBacktests: z.boolean(),
  duplicateCount: z.number().int(),
  retentionStatus: retentionStatusSchema,
});

const backtestDatasetRetentionReportSchema = z.object({
  totalDatasets: z.number().int(),
  activeDatasets: z.number().int(),
  archivedDatasets: z.number().int(),
  archiveCandidateDatasets: z.number().int(),
  duplicateDatasetCount: z.number().int(),
  referencedDatasetCount: z.number().int(),
  oldestActiveUploadedAt: z.string().nullable(),
  newestUploadedAt: z.string().nullable(),
});

const backtestHistoryItemSchema = z.object({
  id: z.number().int(),
  strategyId: z.string().min(1),
  datasetName: z.string().nullable(),
  experimentName: z.string().min(1),
  symbol: z.string().min(1),
  timeframe: z.string().min(1),
  executionStatus: executionStatusSchema,
  validationStatus: validationStatusSchema,
  feesBps: z.number().int(),
  slippageBps: z.number().int(),
  timestamp: z.string().min(1),
  initialBalance: z.number(),
  finalBalance: z.number(),
  executionStage: executionStageSchema,
  progressPercent: z.number().int(),
  processedCandles: z.number().int(),
  totalCandles: z.number().int(),
  currentDataTimestamp: z.string().nullable(),
  statusMessage: z.string().nullable(),
  lastProgressAt: z.string().nullable(),
  startedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
  asyncMonitor: asyncTaskMonitorSchema,
});

const backtestHistoryPageSchema = z.object({
  items: z.array(backtestHistoryItemSchema),
  total: z.number().int(),
  page: z.number().int(),
  pageSize: z.number().int(),
});

const backtestEquityPointSchema = z.object({
  timestamp: z.string().min(1),
  equity: z.number(),
  drawdownPct: z.number(),
});

const backtestTradeSeriesItemSchema = z.object({
  symbol: z.string().min(1),
  side: tradeSideSchema,
  entryTime: z.string().min(1),
  exitTime: z.string().min(1),
  entryPrice: z.number(),
  exitPrice: z.number(),
  quantity: z.number(),
  entryValue: z.number(),
  exitValue: z.number(),
  returnPct: z.number(),
});

const backtestTelemetryPointSchema = z.object({
  timestamp: z.string().min(1),
  open: z.number(),
  high: z.number(),
  low: z.number(),
  close: z.number(),
  volume: z.number(),
  exposurePct: z.number(),
  regime: regimeSchema,
});

const backtestActionMarkerSchema = z.object({
  timestamp: z.string().min(1),
  action: actionTypeSchema,
  price: z.number(),
  label: z.string().min(1),
});

const backtestIndicatorPointSchema = z.object({
  timestamp: z.string().min(1),
  value: z.number().nullable(),
});

const backtestIndicatorSeriesSchema = z.object({
  key: z.string().min(1),
  label: z.string().min(1),
  pane: indicatorPaneSchema,
  points: z.array(backtestIndicatorPointSchema),
});

const backtestStrategyMetricSchema = z.object({
  key: z.string().min(1),
  label: z.string().min(1),
  value: z.number(),
  displayValue: z.string().min(1),
  description: z.string().min(1),
});

const backtestSymbolTelemetrySchema = z.object({
  symbol: z.string().min(1),
  points: z.array(backtestTelemetryPointSchema),
  actions: z.array(backtestActionMarkerSchema),
  indicators: z.array(backtestIndicatorSeriesSchema),
});

const backtestDetailsSchema = backtestHistoryItemSchema.extend({
  datasetId: z.number().int().nullable(),
  experimentKey: z.string().min(1),
  datasetChecksumSha256: z.string().nullable(),
  datasetSchemaVersion: z.string().nullable(),
  datasetUploadedAt: z.string().nullable(),
  datasetArchived: z.boolean().nullable(),
  sharpeRatio: z.number(),
  profitFactor: z.number(),
  winRate: z.number(),
  maxDrawdown: z.number(),
  totalTrades: z.number().int(),
  startDate: z.string().min(1),
  endDate: z.string().min(1),
  errorMessage: z.string().nullable(),
  availableTelemetrySymbols: z.array(z.string().min(1)).default([]),
  strategyMetrics: z.array(backtestStrategyMetricSchema).default([]),
});

const backtestSummarySchema = backtestHistoryItemSchema.extend({
  datasetId: z.number().int().nullable(),
  experimentKey: z.string().min(1),
  datasetChecksumSha256: z.string().nullable(),
  datasetSchemaVersion: z.string().nullable(),
  datasetUploadedAt: z.string().nullable(),
  datasetArchived: z.boolean().nullable(),
  sharpeRatio: z.number(),
  profitFactor: z.number(),
  winRate: z.number(),
  maxDrawdown: z.number(),
  totalTrades: z.number().int(),
  startDate: z.string().min(1),
  endDate: z.string().min(1),
  errorMessage: z.string().nullable(),
  strategyMetrics: z.array(backtestStrategyMetricSchema).default([]),
});

const backtestComparisonItemSchema = z.object({
  id: z.number().int(),
  strategyId: z.string().min(1),
  datasetName: z.string().nullable(),
  datasetChecksumSha256: z.string().nullable(),
  datasetSchemaVersion: z.string().nullable(),
  datasetUploadedAt: z.string().nullable(),
  datasetArchived: z.boolean().nullable(),
  symbol: z.string().min(1),
  timeframe: z.string().min(1),
  executionStatus: executionStatusSchema,
  validationStatus: validationStatusSchema,
  feesBps: z.number().int(),
  slippageBps: z.number().int(),
  timestamp: z.string().min(1),
  initialBalance: z.number(),
  finalBalance: z.number(),
  totalReturnPercent: z.number(),
  sharpeRatio: z.number(),
  profitFactor: z.number(),
  winRate: z.number(),
  maxDrawdown: z.number(),
  totalTrades: z.number().int(),
  finalBalanceDelta: z.number(),
  totalReturnDeltaPercent: z.number(),
});

const backtestComparisonResponseSchema = z.object({
  baselineBacktestId: z.number().int(),
  items: z.array(backtestComparisonItemSchema),
});

const backtestExperimentSummarySchema = z.object({
  experimentKey: z.string().min(1),
  experimentName: z.string().min(1),
  latestBacktestId: z.number().int(),
  strategyId: z.string().min(1),
  datasetName: z.string().nullable(),
  symbol: z.string().min(1),
  timeframe: z.string().min(1),
  latestExecutionStatus: executionStatusSchema,
  latestValidationStatus: validationStatusSchema,
  runCount: z.number().int(),
  latestRunAt: z.string().min(1),
  averageReturnPercent: z.number(),
  bestFinalBalance: z.number(),
  worstMaxDrawdown: z.number(),
});

const backtestRunSubmissionSchema = z.object({
  id: z.number().int(),
  status: executionStatusSchema,
  submittedAt: z.string().min(1),
  asyncMonitor: asyncTaskMonitorSchema,
});

const backtestTelemetryQueryResponseSchema = z.object({
  requestedSymbol: z.string().nullable(),
  resolvedSymbol: z.string().min(1),
  availableSymbols: z.array(z.string().min(1)).default([]),
  telemetry: backtestSymbolTelemetrySchema,
});

export const normalizeBacktestAlgorithms = (
  response: RawBacktestAlgorithm[]
): BacktestAlgorithm[] => z.array(backtestAlgorithmSchema).parse(response);

export const normalizeBacktestDatasets = (response: RawBacktestDataset[]): BacktestDataset[] =>
  z.array(backtestDatasetSchema).parse(response);

export const normalizeBacktestDataset = (response: RawBacktestDataset): BacktestDataset =>
  backtestDatasetSchema.parse(response);

export const normalizeBacktestDatasetRetentionReport = (
  response: RawBacktestDatasetRetentionReport
): BacktestDatasetRetentionReport => backtestDatasetRetentionReportSchema.parse(response);

export const normalizeBacktestHistory = (
  response: RawBacktestHistoryPageResponse,
  fallback: { page: number; pageSize: number }
): BacktestHistoryResult =>
  backtestHistoryPageSchema.parse({
    items: (response.items ?? []).map((item) => ({
      ...item,
      asyncMonitor: normalizeAsyncTaskMonitor(
        item.asyncMonitor,
        item.executionStatus,
        item.lastProgressAt
      ),
    })),
    total: response.total ?? response.items?.length ?? 0,
    page: response.page ?? fallback.page,
    pageSize: response.pageSize ?? fallback.pageSize,
  });

export const normalizeBacktestDetails = (response: RawBacktestDetails): BacktestDetails =>
  backtestDetailsSchema.parse({
    ...response,
    asyncMonitor: normalizeAsyncTaskMonitor(
      response.asyncMonitor,
      response.executionStatus,
      response.lastProgressAt
    ),
  });

export const normalizeBacktestEquityCurve = (
  response: RawBacktestEquityPoint[]
): BacktestEquityPoint[] => z.array(backtestEquityPointSchema).parse(response);

export const normalizeBacktestTradeSeries = (
  response: RawBacktestTradeSeriesItem[]
): BacktestTradeSeriesItem[] => z.array(backtestTradeSeriesItemSchema).parse(response);

export const normalizeBacktestTelemetryResponse = (
  response: RawBacktestTelemetryQueryResponse
): BacktestTelemetryQueryResponse => backtestTelemetryQueryResponseSchema.parse(response);

export const normalizeBacktestSummary = (response: RawBacktestSummary): BacktestSummary =>
  backtestSummarySchema.parse({
    ...response,
    asyncMonitor: normalizeAsyncTaskMonitor(
      response.asyncMonitor,
      response.executionStatus,
      response.lastProgressAt
    ),
  });

export const normalizeBacktestExperimentSummaries = (
  response: RawBacktestExperimentSummary[]
): BacktestExperimentSummary[] => z.array(backtestExperimentSummarySchema).parse(response);

export const normalizeBacktestComparisonResponse = (
  response: RawBacktestComparisonResponse
): BacktestComparisonResponse => backtestComparisonResponseSchema.parse(response);

export const normalizeBacktestRunSubmission = (
  response: RawBacktestRunSubmission
): BacktestRunSubmission =>
  backtestRunSubmissionSchema.parse({
    ...response,
    asyncMonitor: normalizeAsyncTaskMonitor(response.asyncMonitor, response.status, response.submittedAt),
  });

const normalizeAsyncTaskMonitor = (
  monitor: RawAsyncTaskMonitor | undefined,
  executionStatus: string | undefined,
  timestamp: string | null | undefined
): RawAsyncTaskMonitor =>
  monitor ?? {
    state:
      executionStatus === 'PENDING'
        ? 'QUEUED'
        : executionStatus === 'RUNNING'
          ? 'RUNNING'
          : executionStatus === 'FAILED'
            ? 'FAILED'
            : 'COMPLETED',
    attemptCount: executionStatus === 'PENDING' ? 0 : 1,
    maxAttempts: 1,
    nextRetryAt: null,
    retryEligible: executionStatus === 'FAILED' || executionStatus === 'COMPLETED',
    timedOut: false,
    timeoutThresholdSeconds: timestamp ? 120 : null,
  };

export const toRunBacktestRequest = (payload: RunBacktestPayload): RawBacktestRunRequest => {
  const request: RawBacktestRunRequest = {
    algorithmType: payload.algorithmType,
    datasetId: payload.datasetId,
    timeframe: payload.timeframe,
    startDate: payload.startDate,
    endDate: payload.endDate,
    initialBalance: payload.initialBalance,
    feesBps: payload.feesBps,
    slippageBps: payload.slippageBps,
  };

  const experimentName = payload.experimentName?.trim();
  if (experimentName) {
    request.experimentName = experimentName;
  }

  const symbol = payload.symbol?.trim();
  if (symbol) {
    request.symbol = symbol;
  }

  return request;
};
