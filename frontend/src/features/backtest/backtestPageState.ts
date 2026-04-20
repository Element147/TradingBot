import type {
  BacktestAlgorithm,
  BacktestDataset,
  BacktestExecutionStatus,
  BacktestHistoryItem,
  BacktestValidationStatus,
} from './backtestApi';
import type { BacktestConfigFormState } from './backtestConfigForm';

import { formatDateTime, formatDistanceToNow } from '@/utils/formatters';

export const initialBacktestForm: BacktestConfigFormState = {
  algorithmType: 'BUY_AND_HOLD',
  datasetId: '',
  experimentName: '',
  symbol: '',
  timeframe: '1h',
  startDate: '2025-01-01',
  endDate: '2025-12-31',
  initialBalance: '1000',
  feesBps: '10',
  slippageBps: '3',
};

export const parseSymbols = (symbolsCsv: string): string[] =>
  symbolsCsv
    .split(',')
    .map((symbol) => symbol.trim())
    .filter((symbol) => symbol.length > 0);

export const resolveFormState = (
  form: BacktestConfigFormState,
  datasets: BacktestDataset[],
  algorithms: BacktestAlgorithm[]
): BacktestConfigFormState => {
  const selectedDatasetId = form.datasetId || (datasets[0] ? String(datasets[0].id) : '');
  const selectedDataset = datasets.find((dataset) => String(dataset.id) === selectedDatasetId) ?? null;
  const selectedAlgorithm = algorithms.find((algorithm) => algorithm.id === form.algorithmType) ?? null;
  const requiresDatasetUniverse = selectedAlgorithm?.selectionMode === 'DATASET_UNIVERSE';
  const datasetSymbols = selectedDataset ? parseSymbols(selectedDataset.symbolsCsv) : [];
  const resolvedSymbol =
    requiresDatasetUniverse
      ? ''
      : datasetSymbols.length === 0 || datasetSymbols.includes(form.symbol)
        ? form.symbol
        : datasetSymbols[0];

  return {
    ...form,
    datasetId: selectedDatasetId,
    symbol: resolvedSymbol,
  };
};

export const validationColor = (
  value: BacktestValidationStatus
): 'success.main' | 'error.main' | 'warning.main' => {
  if (value === 'PASSED' || value === 'PRODUCTION_READY') {
    return 'success.main';
  }
  if (value === 'FAILED') {
    return 'error.main';
  }
  return 'warning.main';
};

export const retentionChipColor = (
  status: BacktestDataset['retentionStatus']
): 'success' | 'warning' | 'default' => {
  if (status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'ACTIVE_DUPLICATE_RETAINED' || status === 'ACTIVE_STALE_RETAINED') {
    return 'default';
  }
  return 'warning';
};

export const retentionLabel = (status: BacktestDataset['retentionStatus']): string => {
  switch (status) {
    case 'ACTIVE_DUPLICATE_RETAINED':
      return 'Duplicate retained';
    case 'ACTIVE_STALE_RETAINED':
      return 'Stale retained';
    case 'ARCHIVE_CANDIDATE_DUPLICATE':
      return 'Archive candidate: duplicate';
    case 'ARCHIVE_CANDIDATE_UNUSED':
      return 'Archive candidate: unused';
    case 'ARCHIVED':
      return 'Archived';
    default:
      return 'Active';
  }
};

export const executionStatusColor = (
  status: BacktestExecutionStatus
): 'default' | 'info' | 'success' | 'error' => {
  switch (status) {
    case 'RUNNING':
      return 'info';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    default:
      return 'default';
  }
};

export const executionStageLabel = (stage: BacktestHistoryItem['executionStage']): string => {
  switch (stage) {
    case 'VALIDATING_REQUEST':
      return 'Validating request';
    case 'LOADING_DATASET':
      return 'Loading dataset';
    case 'FILTERING_CANDLES':
      return 'Filtering candles';
    case 'SIMULATING':
      return 'Simulating';
    case 'PERSISTING_RESULTS':
      return 'Saving results';
    case 'COMPLETED':
      return 'Completed';
    case 'FAILED':
      return 'Failed';
    default:
      return 'Queued';
  }
};

export const executionProgressValue = (item: BacktestHistoryItem): number =>
  Math.max(0, Math.min(item.progressPercent ?? 0, 100));

export const executionStageDescription = (item: BacktestHistoryItem): string =>
  item.statusMessage?.trim() ||
  (item.executionStatus === 'FAILED'
    ? 'Execution stopped before finishing. Open the run details for the error reason.'
    : 'Awaiting the next progress update from the backend worker.');

export const percentLeft = (item: BacktestHistoryItem): number =>
  Math.max(0, 100 - executionProgressValue(item));

export const formatProgressTimestamp = (value: string | null): string =>
  value ? formatDateTime(value) : 'Not started yet';

export const formatLastUpdate = (value: string | null): string =>
  value ? formatDistanceToNow(new Date(value)) : 'No updates yet';

export const formatLiveEventTimestamp = (value: string | null): string =>
  value ? formatDistanceToNow(new Date(value)) : 'No live progress event received yet';

export const isExecutionActive = (item: BacktestHistoryItem): boolean =>
  item.executionStatus === 'PENDING' || item.executionStatus === 'RUNNING';
