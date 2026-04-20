import AddIcon from '@mui/icons-material/Add';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import DownloadIcon from '@mui/icons-material/Download';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import ReplayIcon from '@mui/icons-material/Replay';
import RestoreFromTrashIcon from '@mui/icons-material/RestoreFromTrash';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';

import type {
  BacktestAlgorithm,
  BacktestComparisonResponse,
  BacktestDataset,
  BacktestDatasetRetentionReport,
  BacktestExperimentSummary,
  BacktestHistoryItem,
} from './backtestApi';
import { BacktestComparisonPanel } from './BacktestComparisonPanel';
import {
  executionProgressValue,
  executionStageDescription,
  executionStageLabel,
  executionStatusColor,
  formatLastUpdate,
  formatLiveEventTimestamp,
  formatProgressTimestamp,
  isExecutionActive,
  percentLeft,
  retentionChipColor,
  retentionLabel,
  validationColor,
} from './backtestPageState';
import { formatBacktestMarketLabel } from './backtestTypes';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { KeyValueGrid } from '@/components/ui/KeyValueGrid';
import type { StrategyProfile } from '@/features/strategies/strategyProfiles';
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatPercentage,
} from '@/utils/formatters';

type SortDirection = 'asc' | 'desc';
type DatasetSortField =
  | 'name'
  | 'symbolsCsv'
  | 'rowCount'
  | 'retentionStatus'
  | 'usageCount'
  | 'lastUsedAt'
  | 'uploadedAt'
  | 'schemaChecksum';
type HistorySortField =
  | 'id'
  | 'strategyId'
  | 'datasetName'
  | 'experimentName'
  | 'market'
  | 'executionStatus'
  | 'validationStatus'
  | 'feesBps'
  | 'slippageBps';

interface HeaderCellProps {
  label: string;
  description: string;
}

function HeaderCellWithTooltip({ label, description }: HeaderCellProps) {
  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      <span>{label}</span>
      <Tooltip title={description} arrow>
        <InfoOutlinedIcon fontSize="inherit" color="action" sx={{ cursor: 'help' }} />
      </Tooltip>
    </Stack>
  );
}

interface SortableHeaderCellProps extends HeaderCellProps {
  active: boolean;
  direction: SortDirection;
  onClick: () => void;
  align?: 'left' | 'right';
}

function SortableHeaderCell({
  label,
  description,
  active,
  direction,
  onClick,
  align = 'left',
}: SortableHeaderCellProps) {
  return (
    <Stack
      direction="row"
      spacing={0.5}
      alignItems="center"
      justifyContent={align === 'right' ? 'flex-end' : 'flex-start'}
    >
      <TableSortLabel active={active} direction={direction} onClick={onClick}>
        {label}
      </TableSortLabel>
      <Tooltip title={description} arrow>
        <InfoOutlinedIcon fontSize="inherit" color="action" sx={{ cursor: 'help' }} />
      </Tooltip>
    </Stack>
  );
}

const asyncStateLabel = (item: BacktestHistoryItem) =>
  item.asyncMonitor?.state ?? (item.executionStatus === 'PENDING' ? 'QUEUED' : item.executionStatus);

const compareValues = (
  left: number | string,
  right: number | string,
  direction: SortDirection
) => {
  const multiplier = direction === 'asc' ? 1 : -1;

  if (typeof left === 'number' && typeof right === 'number') {
    return (left - right) * multiplier;
  }

  return String(left).localeCompare(String(right), undefined, { sensitivity: 'base' }) * multiplier;
};

const compareDates = (left: string | null, right: string | null, direction: SortDirection) =>
  compareValues(left ? new Date(left).getTime() : 0, right ? new Date(right).getTime() : 0, direction);

interface BacktestTransportStatusAlertProps {
  transportConnected: boolean;
  lastLiveEventAt: string | null;
  websocketError: string | null;
}

export function BacktestTransportStatusAlert({
  transportConnected,
  lastLiveEventAt,
  websocketError,
}: BacktestTransportStatusAlertProps) {
  return (
    <Alert severity={transportConnected ? 'success' : 'info'} sx={{ mb: 2 }}>
      Backtest transport: {transportConnected ? 'live WebSocket stream connected' : 'fallback polling active'}.
      {' '}Last pushed progress event: {formatLiveEventTimestamp(lastLiveEventAt)}.
      {' '}Safety poll cadence: {transportConnected ? '30 seconds' : '2 seconds'}.
      {!transportConnected && websocketError ? ` Stream status: ${websocketError}.` : ''}
    </Alert>
  );
}

interface BacktestTrackedRunCardProps {
  trackedRun: BacktestHistoryItem;
  transportConnected: boolean;
  lastLiveEventAt: string | null;
}

export function BacktestTrackedRunCard({
  trackedRun,
  transportConnected,
  lastLiveEventAt,
}: BacktestTrackedRunCardProps) {
  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="h6">Current Run Progress</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                Tracking run #{trackedRun.id} for {trackedRun.strategyId} on{' '}
                {trackedRun.datasetName ?? 'dataset'}.
              </Typography>
            </Box>
            <Chip
              label={asyncStateLabel(trackedRun)}
              color={executionStatusColor(trackedRun.executionStatus)}
              variant={trackedRun.executionStatus === 'COMPLETED' ? 'filled' : 'outlined'}
            />
          </Stack>
          <LinearProgress
            variant="determinate"
            value={executionProgressValue(trackedRun)}
            color={trackedRun.executionStatus === 'FAILED' ? 'error' : 'primary'}
            sx={{ height: 8, borderRadius: 1 }}
          />
          <KeyValueGrid
            items={[
              {
                label: 'Transport',
                value: transportConnected ? 'WebSocket live' : 'Polling fallback',
                tone: transportConnected ? 'success' : 'warning',
              },
              { label: 'Stage', value: executionStageLabel(trackedRun.executionStage) },
              { label: 'Done', value: `${executionProgressValue(trackedRun)}%` },
              { label: 'Left', value: `${percentLeft(trackedRun)}%` },
              {
                label: 'Current data date',
                value: formatProgressTimestamp(trackedRun.currentDataTimestamp),
              },
              {
                label: 'Candles',
                value: `${formatNumber(trackedRun.processedCandles)} / ${formatNumber(trackedRun.totalCandles)}`,
              },
              { label: 'Last backend update', value: formatLastUpdate(trackedRun.lastProgressAt) },
              { label: 'Last pushed event', value: formatLiveEventTimestamp(lastLiveEventAt) },
              {
                label: 'Attempts',
                value: trackedRun.asyncMonitor
                  ? `${trackedRun.asyncMonitor.attemptCount} / ${trackedRun.asyncMonitor.maxAttempts ?? 1}`
                  : '1 / 1',
              },
            ]}
          />
          <Typography variant="body2" color="text.secondary">
            {executionStageDescription(trackedRun)}
          </Typography>
          {isExecutionActive(trackedRun) && !transportConnected ? (
            <Alert severity="warning">
              Live stream is not connected, so this page is temporarily relying on polling for
              execution updates.
            </Alert>
          ) : null}
          {trackedRun.asyncMonitor?.timedOut ? (
            <Alert severity="error">
              This run has not reported progress within the expected timeout window. Use replay to
              restart it if the worker does not recover.
            </Alert>
          ) : null}
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip
              size="small"
              label={`Validation: ${trackedRun.validationStatus}`}
              sx={{ color: validationColor(trackedRun.validationStatus) }}
              variant="outlined"
            />
            <Chip
              size="small"
              label={`${formatBacktestMarketLabel(trackedRun.symbol)} (${trackedRun.timeframe})`}
              variant="outlined"
            />
            <Chip
              size="small"
              label={`${trackedRun.feesBps} / ${trackedRun.slippageBps} bps`}
              variant="outlined"
            />
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

interface BacktestDatasetPanelProps {
  datasetName: string;
  datasetFile: File | null;
  retentionReport?: BacktestDatasetRetentionReport;
  datasets: BacktestDataset[];
  hasActiveDatasets: boolean;
  isUploading: boolean;
  datasetLifecycleBusy: boolean;
  onDatasetNameChange: (value: string) => void;
  onDatasetFileChange: (file: File | null) => void;
  onUploadDataset: () => void;
  onDownloadDataset: (datasetId: number) => void | Promise<void>;
  onArchiveDataset: (dataset: BacktestDataset) => void | Promise<void>;
  onRestoreDataset: (datasetId: number) => void | Promise<void>;
}

export function BacktestDatasetPanel({
  datasetName,
  datasetFile,
  retentionReport,
  datasets,
  hasActiveDatasets,
  isUploading,
  datasetLifecycleBusy,
  onDatasetNameChange,
  onDatasetFileChange,
  onUploadDataset,
  onDownloadDataset,
  onArchiveDataset,
  onRestoreDataset,
}: BacktestDatasetPanelProps) {
  const [sortField, setSortField] = useState<DatasetSortField>('uploadedAt');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

  const sortedDatasets = useMemo(() => {
    const items = [...datasets];

    items.sort((left, right) => {
      switch (sortField) {
        case 'name':
        case 'symbolsCsv':
        case 'retentionStatus':
          return compareValues(left[sortField], right[sortField], sortDirection);
        case 'rowCount':
        case 'usageCount':
          return compareValues(left[sortField], right[sortField], sortDirection);
        case 'lastUsedAt':
          return compareDates(left.lastUsedAt, right.lastUsedAt, sortDirection);
        case 'uploadedAt':
          return compareDates(left.uploadedAt, right.uploadedAt, sortDirection);
        case 'schemaChecksum':
          return compareValues(
            `${left.schemaVersion} ${left.checksumSha256}`,
            `${right.schemaVersion} ${right.checksumSha256}`,
            sortDirection
          );
        default:
          return 0;
      }
    });

    return items;
  }, [datasets, sortDirection, sortField]);

  const toggleSort = (field: DatasetSortField) => {
    if (sortField === field) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
      return;
    }

    setSortField(field);
    setSortDirection(field === 'uploadedAt' || field === 'lastUsedAt' ? 'desc' : 'asc');
  };

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Dataset lifecycle
        </Typography>
        <Stack spacing={2}>
          <Alert severity="info">
            Beginner tip: if you are unsure, start with one clean symbol dataset first, then move to
            multi-symbol universe strategies after you understand the baseline behavior.
          </Alert>
          {retentionReport ? (
            <Alert severity={retentionReport.archiveCandidateDatasets > 0 ? 'warning' : 'info'}>
              Active: {retentionReport.activeDatasets} | Archived: {retentionReport.archivedDatasets}
              {' '}| Archive candidates: {retentionReport.archiveCandidateDatasets} | Referenced by
              backtests: {retentionReport.referencedDatasetCount}
            </Alert>
          ) : null}
          <FieldTooltip title="Human-readable dataset label. Clear naming prevents running backtests on the wrong file.">
            <TextField
              label="Dataset Name (optional)"
              value={datasetName}
              onChange={(event) => onDatasetNameChange(event.target.value)}
              placeholder="BTC 1h 2025"
              helperText="Optional label to identify symbol, timeframe, and date range."
            />
          </FieldTooltip>
          <FieldTooltip title="CSV upload defines the historical data source. Incorrect format or timeframe invalidates results.">
            <Button variant="outlined" component="label">
              {datasetFile ? `Selected: ${datasetFile.name}` : 'Choose CSV File'}
              <input
                hidden
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => onDatasetFileChange(event.target.files?.[0] ?? null)}
              />
            </Button>
          </FieldTooltip>
          <Button variant="contained" onClick={onUploadDataset} disabled={!datasetFile || isUploading}>
            Upload Dataset
          </Button>
          <Typography variant="caption" color="text.secondary">
            CSV format: timestamp,symbol,open,high,low,close,volume
          </Typography>
          {!hasActiveDatasets ? (
            <Alert severity="warning">
              No active datasets are available for new runs. Restore an archived dataset or upload a
              new CSV.
            </Alert>
          ) : null}
          {datasets.length > 0 ? (
            <>
              <Divider />
              <Stack spacing={1}>
                <Typography variant="subtitle2">Sortable dataset inventory</Typography>
                <TableContainer>
                  <Table size="small" sx={{ minWidth: 1260 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>
                          <SortableHeaderCell
                            label="Name"
                            description="Dataset label and original upload file."
                            active={sortField === 'name'}
                            direction={sortDirection}
                            onClick={() => toggleSort('name')}
                          />
                        </TableCell>
                        <TableCell>
                          <SortableHeaderCell
                            label="Symbols"
                            description="Symbols available for single-symbol or dataset-universe runs."
                            active={sortField === 'symbolsCsv'}
                            direction={sortDirection}
                            onClick={() => toggleSort('symbolsCsv')}
                          />
                        </TableCell>
                        <TableCell align="right">
                          <SortableHeaderCell
                            label="Rows"
                            description="Total number of historical rows in the uploaded file."
                            active={sortField === 'rowCount'}
                            direction={sortDirection}
                            onClick={() => toggleSort('rowCount')}
                            align="right"
                          />
                        </TableCell>
                        <TableCell>
                          <SortableHeaderCell
                            label="Retention"
                            description="Lifecycle state for active, retained duplicate, or archived datasets."
                            active={sortField === 'retentionStatus'}
                            direction={sortDirection}
                            onClick={() => toggleSort('retentionStatus')}
                          />
                        </TableCell>
                        <TableCell align="right">
                          <SortableHeaderCell
                            label="Usage"
                            description="Number of runs referencing this dataset."
                            active={sortField === 'usageCount'}
                            direction={sortDirection}
                            onClick={() => toggleSort('usageCount')}
                            align="right"
                          />
                        </TableCell>
                        <TableCell>
                          <SortableHeaderCell
                            label="Last used"
                            description="Most recent run timestamp using this dataset."
                            active={sortField === 'lastUsedAt'}
                            direction={sortDirection}
                            onClick={() => toggleSort('lastUsedAt')}
                          />
                        </TableCell>
                        <TableCell>
                          <SortableHeaderCell
                            label="Uploaded"
                            description="Upload timestamp for sorting recent versus older datasets."
                            active={sortField === 'uploadedAt'}
                            direction={sortDirection}
                            onClick={() => toggleSort('uploadedAt')}
                          />
                        </TableCell>
                        <TableCell>
                          <SortableHeaderCell
                            label="Schema / checksum"
                            description="Schema version and dataset proof used for reproducibility."
                            active={sortField === 'schemaChecksum'}
                            direction={sortDirection}
                            onClick={() => toggleSort('schemaChecksum')}
                          />
                        </TableCell>
                        <TableCell>
                          <HeaderCellWithTooltip
                            label="Actions"
                            description="Download, archive, or restore the dataset."
                          />
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {sortedDatasets.map((dataset) => (
                        <TableRow key={dataset.id} hover>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {dataset.name}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {dataset.originalFilename}
                              </Typography>
                              {dataset.archived && dataset.archiveReason ? (
                                <Typography variant="caption" color="text.secondary">
                                  Archive reason: {dataset.archiveReason}
                                </Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">{dataset.symbolsCsv}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {formatDateTime(dataset.dataStart)} to {formatDateTime(dataset.dataEnd)}
                            </Typography>
                          </TableCell>
                          <TableCell align="right">{formatNumber(dataset.rowCount)}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={retentionChipColor(dataset.retentionStatus)}
                              label={retentionLabel(dataset.retentionStatus)}
                            />
                          </TableCell>
                          <TableCell align="right">
                            <Typography variant="body2">{formatNumber(dataset.usageCount)}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {dataset.usedByBacktests ? 'Referenced by runs' : 'Not referenced yet'}
                              {dataset.duplicateCount > 1
                                ? ` | Duplicates: ${dataset.duplicateCount}`
                                : ''}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {dataset.lastUsedAt ? formatDateTime(dataset.lastUsedAt) : 'Never used'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">{formatDateTime(dataset.uploadedAt)}</Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">{dataset.schemaVersion}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {dataset.checksumSha256.slice(0, 12)}...
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                              <Button
                                variant="outlined"
                                size="small"
                                startIcon={<DownloadIcon />}
                                onClick={() => void onDownloadDataset(dataset.id)}
                              >
                                Download
                              </Button>
                              {dataset.archived ? (
                                <Button
                                  variant="outlined"
                                  size="small"
                                  startIcon={<RestoreFromTrashIcon />}
                                  disabled={datasetLifecycleBusy}
                                  onClick={() => void onRestoreDataset(dataset.id)}
                                >
                                  Restore
                                </Button>
                              ) : (
                                <Button
                                  variant="outlined"
                                  size="small"
                                  color="warning"
                                  startIcon={<ArchiveOutlinedIcon />}
                                  disabled={datasetLifecycleBusy}
                                  onClick={() => void onArchiveDataset(dataset)}
                                >
                                  Archive
                                </Button>
                              )}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            </>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );
}

interface BacktestRunLauncherPanelProps {
  selectedAlgorithm: BacktestAlgorithm | null;
  selectedAlgorithmProfile: StrategyProfile | null;
  requiresDatasetUniverse: boolean;
  hasActiveDatasets: boolean;
  onOpenConfigModal: () => void;
}

export function BacktestRunLauncherPanel({
  selectedAlgorithm,
  selectedAlgorithmProfile,
  requiresDatasetUniverse,
  hasActiveDatasets,
  onOpenConfigModal,
}: BacktestRunLauncherPanelProps) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Run Backtest
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Not sure what to choose? Start with <strong>Buy and Hold</strong> or{' '}
          <strong>SMA Crossover</strong>, a single active dataset, the recommended timeframe, and the
          default cost assumptions.
        </Alert>
        {selectedAlgorithm ? (
          <Alert severity="info" sx={{ mb: 2 }}>
            <strong>{selectedAlgorithm.label}:</strong> {selectedAlgorithm.description}
            {selectedAlgorithmProfile ? ` Best use: ${selectedAlgorithmProfile.bestFor}` : ''}
            {requiresDatasetUniverse ? ' Uses all symbols in the selected dataset.' : ''}
          </Alert>
        ) : null}
        {!hasActiveDatasets ? (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Upload or restore an active dataset before opening the backtest configuration dialog.
          </Alert>
        ) : null}
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          fullWidth
          onClick={onOpenConfigModal}
          disabled={!hasActiveDatasets}
        >
          Run New Backtest
        </Button>
      </CardContent>
    </Card>
  );
}

interface BacktestExperimentSummariesPanelProps {
  experimentSummaries: BacktestExperimentSummary[];
}

export function BacktestExperimentSummariesPanel({
  experimentSummaries,
}: BacktestExperimentSummariesPanelProps) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Experiment Summaries
        </Typography>
        {experimentSummaries.length === 0 ? (
          <Alert severity="info">Named experiment groups will appear here once runs are recorded.</Alert>
        ) : (
          <Stack spacing={1.25}>
            {experimentSummaries.slice(0, 6).map((summary) => (
              <Box
                key={summary.experimentKey}
                sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1.25 }}
              >
                <Stack
                  direction={{ xs: 'column', md: 'row' }}
                  spacing={1}
                  justifyContent="space-between"
                  alignItems={{ xs: 'flex-start', md: 'center' }}
                >
                  <Box>
                    <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                      {summary.experimentName}
                    </Typography>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                      <Chip size="small" label={`Run #${summary.latestBacktestId}`} variant="outlined" />
                      <Chip size="small" label={summary.strategyId} variant="outlined" />
                      <Chip
                        size="small"
                        label={`${formatBacktestMarketLabel(summary.symbol)} (${summary.timeframe})`}
                        variant="outlined"
                      />
                    </Stack>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ mt: 0.75, overflowWrap: 'anywhere' }}
                    >
                      {summary.datasetName ?? 'No dataset label'} | Latest run at{' '}
                      {formatDateTime(summary.latestRunAt)}
                    </Typography>
                    <Stack
                      direction={{ xs: 'column', sm: 'row' }}
                      spacing={{ xs: 0.35, sm: 1.5 }}
                      sx={{ mt: 0.75 }}
                    >
                      <Typography variant="body2" color="text.secondary">
                        Runs: {summary.runCount}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Avg return: {formatPercentage(summary.averageReturnPercent)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Best balance: {formatCurrency(summary.bestFinalBalance)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Worst drawdown: {formatPercentage(summary.worstMaxDrawdown)}
                      </Typography>
                    </Stack>
                  </Box>
                  <Stack direction="row" spacing={1}>
                    <Chip
                      size="small"
                      label={`Exec: ${summary.latestExecutionStatus}`}
                      color={summary.latestExecutionStatus === 'COMPLETED' ? 'success' : 'default'}
                    />
                    <Chip
                      size="small"
                      label={`Validation: ${summary.latestValidationStatus}`}
                      sx={{ color: validationColor(summary.latestValidationStatus) }}
                    />
                  </Stack>
                </Stack>
              </Box>
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

interface BacktestHistoryPanelProps {
  history: BacktestHistoryItem[];
  comparison?: BacktestComparisonResponse;
  comparisonIds: number[];
  selectedId: number | null;
  comparisonIsStale: boolean;
  comparisonErrorMessage: string | null;
  lastLiveEventAt: string | null;
  isLoading: boolean;
  isError: boolean;
  isComparing: boolean;
  isReplaying: boolean;
  isDeletingBacktest: boolean;
  onCompareSelected: () => void | Promise<void>;
  onClearComparison: () => void;
  onToggleComparison: (backtestId: number) => void;
  onSelectRun: (backtestId: number) => void;
  onViewDetails: (backtestId: number) => void;
  onReplayBacktest: (backtestId: number) => void | Promise<void>;
  onDeleteResult: (item: BacktestHistoryItem) => void | Promise<void>;
}

export function BacktestHistoryPanel({
  history,
  comparison,
  comparisonIds,
  selectedId,
  comparisonIsStale,
  comparisonErrorMessage,
  lastLiveEventAt,
  isLoading,
  isError,
  isComparing,
  isReplaying,
  isDeletingBacktest,
  onCompareSelected,
  onClearComparison,
  onToggleComparison,
  onSelectRun,
  onViewDetails,
  onReplayBacktest,
  onDeleteResult,
}: BacktestHistoryPanelProps) {
  const [sortField, setSortField] = useState<HistorySortField>('id');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

  const sortedHistory = useMemo(() => {
    const items = [...history];

    items.sort((left, right) => {
      switch (sortField) {
        case 'id':
        case 'feesBps':
        case 'slippageBps':
          return compareValues(left[sortField], right[sortField], sortDirection);
        case 'strategyId':
        case 'experimentName':
        case 'executionStatus':
        case 'validationStatus':
          return compareValues(left[sortField], right[sortField], sortDirection);
        case 'datasetName':
          return compareValues(left.datasetName ?? '', right.datasetName ?? '', sortDirection);
        case 'market':
          return compareValues(
            `${formatBacktestMarketLabel(left.symbol)} ${left.timeframe}`,
            `${formatBacktestMarketLabel(right.symbol)} ${right.timeframe}`,
            sortDirection
          );
        default:
          return 0;
      }
    });

    return items;
  }, [history, sortDirection, sortField]);

  const toggleSort = (field: HistorySortField) => {
    if (sortField === field) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
      return;
    }

    setSortField(field);
    setSortDirection(field === 'id' ? 'desc' : 'asc');
  };

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Backtest History
        </Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mb: 2 }}>
          <Button
            variant="outlined"
            onClick={() => void onCompareSelected()}
            disabled={comparisonIds.length < 2 || isComparing}
          >
            Compare Selected ({comparisonIds.length})
          </Button>
          <Button
            variant="text"
            onClick={onClearComparison}
            disabled={comparisonIds.length === 0}
          >
            Clear Selection
          </Button>
        </Stack>
        {comparisonIsStale ? (
          <Alert severity="info" sx={{ mb: 2 }}>
            Comparison selection changed. Run compare again to refresh the analysis.
          </Alert>
        ) : null}
        {comparisonErrorMessage ? (
          <Alert severity="error" sx={{ mb: 2 }}>
            {comparisonErrorMessage}
          </Alert>
        ) : null}
        {isLoading ? <Typography>Loading history...</Typography> : null}
        {isError ? <Alert severity="error">Unable to load backtest history.</Alert> : null}

        {!isLoading && !isError && history.length === 0 ? (
          <Alert severity="info">Run history will appear here once backtests are recorded.</Alert>
        ) : null}

        {!isLoading && history.length > 0 ? (
          <TableContainer>
            <Table size="small" sx={{ minWidth: 1320 }}>
              <TableHead>
                <TableRow>
                  <TableCell>
                    <HeaderCellWithTooltip
                      label="Compare"
                      description="Select two or more completed runs to compare them side by side."
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="ID"
                      description="Unique run identifier. Select a row to open full details and charts."
                      active={sortField === 'id'}
                      direction={sortDirection}
                      onClick={() => toggleSort('id')}
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="Algorithm"
                      description="Strategy logic used for this backtest run."
                      active={sortField === 'strategyId'}
                      direction={sortDirection}
                      onClick={() => toggleSort('strategyId')}
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="Dataset"
                      description="Historical file used as market-data input."
                      active={sortField === 'datasetName'}
                      direction={sortDirection}
                      onClick={() => toggleSort('datasetName')}
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="Experiment"
                      description="Repeatable research group label for related runs."
                      active={sortField === 'experimentName'}
                      direction={sortDirection}
                      onClick={() => toggleSort('experimentName')}
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="Market"
                      description="Symbol and timeframe tested in the simulation."
                      active={sortField === 'market'}
                      direction={sortDirection}
                      onClick={() => toggleSort('market')}
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="Status"
                      description="Live execution stage, progress, and backend telemetry for the run."
                      active={sortField === 'executionStatus'}
                      direction={sortDirection}
                      onClick={() => toggleSort('executionStatus')}
                    />
                  </TableCell>
                  <TableCell>
                    <SortableHeaderCell
                      label="Validation"
                      description="Quality gate result from internal checks; not proof of profitability."
                      active={sortField === 'validationStatus'}
                      direction={sortDirection}
                      onClick={() => toggleSort('validationStatus')}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <SortableHeaderCell
                      label="Fees"
                      description="Trading fee assumption in basis points."
                      active={sortField === 'feesBps'}
                      direction={sortDirection}
                      onClick={() => toggleSort('feesBps')}
                      align="right"
                    />
                  </TableCell>
                  <TableCell align="right">
                    <SortableHeaderCell
                      label="Slippage"
                      description="Slippage assumption in basis points."
                      active={sortField === 'slippageBps'}
                      direction={sortDirection}
                      onClick={() => toggleSort('slippageBps')}
                      align="right"
                    />
                  </TableCell>
                  <TableCell>
                    <HeaderCellWithTooltip
                      label="Actions"
                      description="Open detailed results, replay a prior setup, or delete finished runs."
                    />
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sortedHistory.map((item) => (
                  <TableRow
                    key={item.id}
                    hover
                    onClick={() => onSelectRun(item.id)}
                    sx={{ cursor: 'pointer' }}
                    selected={item.id === selectedId}
                  >
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      <Checkbox
                        checked={comparisonIds.includes(item.id)}
                        onChange={() => onToggleComparison(item.id)}
                        inputProps={{ 'aria-label': `Select backtest ${item.id} for comparison` }}
                      />
                    </TableCell>
                    <TableCell>{item.id}</TableCell>
                    <TableCell>{item.strategyId}</TableCell>
                    <TableCell>{item.datasetName ?? '-'}</TableCell>
                    <TableCell>{item.experimentName}</TableCell>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <span>
                          {formatBacktestMarketLabel(item.symbol)} ({item.timeframe})
                        </span>
                        {isExecutionActive(item) ? (
                          <LinearProgress
                            variant="determinate"
                            value={executionProgressValue(item)}
                            sx={{ height: 6, borderRadius: 999, minWidth: 120 }}
                          />
                        ) : null}
                        <Typography variant="caption" color="text.secondary">
                          {item.currentDataTimestamp
                            ? `Current data date: ${formatDateTime(item.currentDataTimestamp)}`
                            : 'Current data date: waiting for first candle'}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <Chip
                          size="small"
                          label={`${asyncStateLabel(item)} | ${executionProgressValue(item)}%`}
                          color={executionStatusColor(item.executionStatus)}
                          variant={item.executionStatus === 'COMPLETED' ? 'filled' : 'outlined'}
                        />
                        <Typography variant="caption" color="text.secondary">
                          {executionStageLabel(item.executionStage)} | {formatNumber(item.processedCandles)}
                          {' '}/ {formatNumber(item.totalCandles)} candles
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {executionStageDescription(item)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Last backend update: {formatLastUpdate(item.lastProgressAt)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Last pushed event: {formatLiveEventTimestamp(lastLiveEventAt)}
                        </Typography>
                        {item.asyncMonitor ? (
                          <Typography variant="caption" color="text.secondary">
                            Attempts: {item.asyncMonitor.attemptCount}
                            {item.asyncMonitor.maxAttempts !== null
                              ? ` / ${item.asyncMonitor.maxAttempts}`
                              : ''}
                            {item.asyncMonitor.timedOut ? ' | Timeout window exceeded' : ''}
                          </Typography>
                        ) : null}
                      </Stack>
                    </TableCell>
                    <TableCell sx={{ color: validationColor(item.validationStatus) }}>
                      {item.validationStatus}
                    </TableCell>
                    <TableCell align="right">{item.feesBps} bps</TableCell>
                    <TableCell align="right">{item.slippageBps} bps</TableCell>
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                        <Button
                          size="small"
                          startIcon={<VisibilityOutlinedIcon />}
                          onClick={() => onViewDetails(item.id)}
                        >
                          Details
                        </Button>
                        <Button
                          size="small"
                          startIcon={<ReplayIcon />}
                          onClick={() => void onReplayBacktest(item.id)}
                          disabled={isReplaying}
                        >
                          Replay
                        </Button>
                        <Button
                          size="small"
                          color="error"
                          startIcon={<DeleteOutlineIcon />}
                          onClick={() => void onDeleteResult(item)}
                          disabled={isDeletingBacktest || isExecutionActive(item)}
                        >
                          Delete
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        ) : null}

        {comparison ? <BacktestComparisonPanel comparison={comparison} /> : null}
      </CardContent>
    </Card>
  );
}
