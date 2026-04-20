export type BacktestSelectionMode = 'SINGLE_SYMBOL' | 'DATASET_UNIVERSE';

export type BacktestExecutionStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type AsyncTaskState =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_RETRY'
  | 'FAILED'
  | 'COMPLETED'
  | 'CANCELLED';

export type BacktestValidationStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'PRODUCTION_READY';

export type BacktestExecutionStage =
  | 'QUEUED'
  | 'VALIDATING_REQUEST'
  | 'LOADING_DATASET'
  | 'FILTERING_CANDLES'
  | 'SIMULATING'
  | 'PERSISTING_RESULTS'
  | 'COMPLETED'
  | 'FAILED';

export type BacktestDatasetRetentionStatus =
  | 'ACTIVE'
  | 'ACTIVE_DUPLICATE_RETAINED'
  | 'ACTIVE_STALE_RETAINED'
  | 'ARCHIVE_CANDIDATE_DUPLICATE'
  | 'ARCHIVE_CANDIDATE_UNUSED'
  | 'ARCHIVED';

export type BacktestTradeSide = 'LONG' | 'SHORT';
export type BacktestActionType = 'BUY' | 'SELL' | 'SHORT' | 'COVER';
export type BacktestIndicatorPane = 'PRICE' | 'OSCILLATOR';
export type BacktestRegime = 'WARMUP' | 'RANGE' | 'TREND_UP' | 'TREND_DOWN';

export const DATASET_UNIVERSE_SYMBOL = 'DATASET_UNIVERSE';
export const DATASET_UNIVERSE_MARKET_LABEL = 'Whole dataset universe';

export type BacktestHistorySortField =
  | 'id'
  | 'strategyId'
  | 'datasetName'
  | 'experimentName'
  | 'market'
  | 'executionStatus'
  | 'validationStatus'
  | 'feesBps'
  | 'slippageBps'
  | 'timestamp';

export const formatBacktestMarketLabel = (symbol: string): string =>
  symbol === DATASET_UNIVERSE_SYMBOL ? DATASET_UNIVERSE_MARKET_LABEL : symbol;

export interface BacktestAlgorithm {
  id: string;
  label: string;
  description: string;
  selectionMode: BacktestSelectionMode;
}

export interface BacktestDataset {
  id: number;
  name: string;
  originalFilename: string;
  rowCount: number;
  symbolsCsv: string;
  dataStart: string;
  dataEnd: string;
  uploadedAt: string;
  checksumSha256: string;
  schemaVersion: string;
  archived: boolean;
  archivedAt: string | null;
  archiveReason: string | null;
  usageCount: number;
  lastUsedAt: string | null;
  usedByBacktests: boolean;
  duplicateCount: number;
  retentionStatus: BacktestDatasetRetentionStatus;
}

export interface BacktestDatasetRetentionReport {
  totalDatasets: number;
  activeDatasets: number;
  archivedDatasets: number;
  archiveCandidateDatasets: number;
  duplicateDatasetCount: number;
  referencedDatasetCount: number;
  oldestActiveUploadedAt: string | null;
  newestUploadedAt: string | null;
}

export interface BacktestHistoryItem {
  id: number;
  strategyId: string;
  datasetName: string | null;
  experimentName: string;
  symbol: string;
  timeframe: string;
  executionStatus: BacktestExecutionStatus;
  validationStatus: BacktestValidationStatus;
  feesBps: number;
  slippageBps: number;
  timestamp: string;
  initialBalance: number;
  finalBalance: number;
  executionStage: BacktestExecutionStage;
  progressPercent: number;
  processedCandles: number;
  totalCandles: number;
  currentDataTimestamp: string | null;
  statusMessage: string | null;
  lastProgressAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  asyncMonitor?: AsyncTaskMonitor;
}

export interface BacktestHistoryQuery {
  page?: number;
  pageSize?: number;
  sortBy?: BacktestHistorySortField;
  sortDirection?: 'asc' | 'desc';
  search?: string;
  strategyId?: string;
  datasetName?: string;
  experimentName?: string;
  market?: string;
  executionStatus?: BacktestExecutionStatus;
  validationStatus?: BacktestValidationStatus;
  feesBpsMin?: number;
  feesBpsMax?: number;
  slippageBpsMin?: number;
  slippageBpsMax?: number;
}

export interface BacktestHistoryResult {
  items: BacktestHistoryItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface BacktestStrategyMetric {
  key: string;
  label: string;
  value: number;
  displayValue: string;
  description: string;
}

export interface BacktestDetails extends BacktestHistoryItem {
  datasetId: number | null;
  experimentKey: string;
  datasetChecksumSha256: string | null;
  datasetSchemaVersion: string | null;
  datasetUploadedAt: string | null;
  datasetArchived: boolean | null;
  sharpeRatio: number;
  profitFactor: number;
  winRate: number;
  maxDrawdown: number;
  totalTrades: number;
  startDate: string;
  endDate: string;
  errorMessage: string | null;
  availableTelemetrySymbols: string[];
  strategyMetrics?: BacktestStrategyMetric[];
  asyncMonitor?: AsyncTaskMonitor;
}

export interface BacktestSummary extends BacktestHistoryItem {
  datasetId: number | null;
  experimentKey: string;
  datasetChecksumSha256: string | null;
  datasetSchemaVersion: string | null;
  datasetUploadedAt: string | null;
  datasetArchived: boolean | null;
  sharpeRatio: number;
  profitFactor: number;
  winRate: number;
  maxDrawdown: number;
  totalTrades: number;
  startDate: string;
  endDate: string;
  errorMessage: string | null;
  strategyMetrics?: BacktestStrategyMetric[];
  asyncMonitor?: AsyncTaskMonitor;
}

export interface AsyncTaskMonitor {
  state: AsyncTaskState;
  attemptCount: number;
  maxAttempts: number | null;
  nextRetryAt: string | null;
  retryEligible: boolean;
  timedOut: boolean;
  timeoutThresholdSeconds: number | null;
}

export interface BacktestSymbolTelemetry {
  symbol: string;
  points: BacktestTelemetryPoint[];
  actions: BacktestActionMarker[];
  indicators: BacktestIndicatorSeries[];
}

export interface BacktestEquityPoint {
  timestamp: string;
  equity: number;
  drawdownPct: number;
}

export interface BacktestTradeSeriesItem {
  symbol: string;
  side: BacktestTradeSide;
  entryTime: string;
  exitTime: string;
  entryPrice: number;
  exitPrice: number;
  quantity: number;
  entryValue: number;
  exitValue: number;
  returnPct: number;
}

export interface BacktestTelemetryQueryResponse {
  requestedSymbol: string | null;
  resolvedSymbol: string;
  availableSymbols: string[];
  telemetry: BacktestSymbolTelemetry;
}

export interface BacktestTelemetryPoint {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  exposurePct: number;
  regime: BacktestRegime;
}

export interface BacktestActionMarker {
  timestamp: string;
  action: BacktestActionType;
  price: number;
  label: string;
}

export interface BacktestIndicatorSeries {
  key: string;
  label: string;
  pane: BacktestIndicatorPane;
  points: Array<{
    timestamp: string;
    value: number | null;
  }>;
}

export interface BacktestComparisonItem {
  id: number;
  strategyId: string;
  datasetName: string | null;
  datasetChecksumSha256: string | null;
  datasetSchemaVersion: string | null;
  datasetUploadedAt: string | null;
  datasetArchived: boolean | null;
  symbol: string;
  timeframe: string;
  executionStatus: BacktestExecutionStatus;
  validationStatus: BacktestValidationStatus;
  feesBps: number;
  slippageBps: number;
  timestamp: string;
  initialBalance: number;
  finalBalance: number;
  totalReturnPercent: number;
  sharpeRatio: number;
  profitFactor: number;
  winRate: number;
  maxDrawdown: number;
  totalTrades: number;
  finalBalanceDelta: number;
  totalReturnDeltaPercent: number;
}

export interface BacktestComparisonResponse {
  baselineBacktestId: number;
  items: BacktestComparisonItem[];
}

export interface BacktestExperimentSummary {
  experimentKey: string;
  experimentName: string;
  latestBacktestId: number;
  strategyId: string;
  datasetName: string | null;
  symbol: string;
  timeframe: string;
  latestExecutionStatus: BacktestExecutionStatus;
  latestValidationStatus: BacktestValidationStatus;
  runCount: number;
  latestRunAt: string;
  averageReturnPercent: number;
  bestFinalBalance: number;
  worstMaxDrawdown: number;
}

export interface RunBacktestPayload {
  algorithmType: string;
  datasetId: number;
  symbol?: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  initialBalance: number;
  feesBps: number;
  slippageBps: number;
  experimentName?: string;
}

export interface BacktestRunSubmission {
  id: number;
  status: BacktestExecutionStatus;
  submittedAt: string;
  asyncMonitor?: AsyncTaskMonitor;
}
