import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import CloudDownloadOutlinedIcon from '@mui/icons-material/CloudDownloadOutlined';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PlayArrowOutlinedIcon from '@mui/icons-material/PlayArrowOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Button,
  Chip,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  Link,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

import type { MarketDataImportJob, MarketDataProvider } from './marketDataApi';
import {
  formatLiveImportEventTimestamp,
  formatOptionalDateTime,
  formatRelativeUpdate,
  providerCredentialMessage,
  statusColor,
  type MarketDataFormState,
} from './marketDataPageState';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { KeyValueGrid } from '@/components/ui/KeyValueGrid';
import { EmptyState, SurfacePanel } from '@/components/ui/Workbench';
import { formatNumber } from '@/utils/formatters';

interface MarketDataTransportAlertProps {
  transportConnected: boolean;
  lastImportEventAt: string | null;
  websocketError: string | null;
}

export function MarketDataTransportAlert({
  transportConnected,
  lastImportEventAt,
  websocketError,
}: MarketDataTransportAlertProps) {
  return (
    <Alert severity={transportConnected ? 'success' : 'info'} sx={{ mb: 2 }}>
      Import transport: {transportConnected ? 'live WebSocket stream connected' : 'fallback polling active'}.
      {' '}Last pushed import event: {formatLiveImportEventTimestamp(lastImportEventAt)}.
      {' '}Safety poll cadence: {transportConnected ? '30 seconds' : '5 seconds'}.
      {!transportConnected && websocketError ? ` Stream status: ${websocketError}.` : ''}
    </Alert>
  );
}

interface MarketDataWaitingJobsAlertProps {
  waitingJobCount: number;
}

export function MarketDataWaitingJobsAlert({
  waitingJobCount,
}: MarketDataWaitingJobsAlertProps) {
  if (waitingJobCount === 0) {
    return null;
  }

  return (
    <Alert severity="info" icon={<HourglassTopIcon />} sx={{ mb: 2 }}>
      {waitingJobCount} job{waitingJobCount === 1 ? '' : 's'} waiting for provider retry windows.
    </Alert>
  );
}

interface MarketDataTelemetryCardProps {
  trackedJob: MarketDataImportJob;
  transportConnected: boolean;
  lastImportEventAt: string | null;
}

export function MarketDataTelemetryCard({
  trackedJob,
  transportConnected,
  lastImportEventAt,
}: MarketDataTelemetryCardProps) {
  const currentSymbolIndex = Math.min(
    trackedJob.currentSymbolIndex + (trackedJob.currentSymbol ? 1 : 0),
    trackedJob.totalSymbols || 0
  );

  return (
    <SurfacePanel
      title="Step 3. Job telemetry"
      description={`Tracking job #${trackedJob.id} for ${trackedJob.providerLabel} on ${trackedJob.symbolsCsv}.`}
      sx={{ mb: 3 }}
      actions={<Chip size="small" color={statusColor(trackedJob.status)} label={trackedJob.status} />}
    >
      <KeyValueGrid
        items={[
          {
            label: 'Transport',
            value: transportConnected ? 'WebSocket live' : 'Polling fallback',
            tone: transportConnected ? 'success' : 'warning',
          },
          {
            label: 'Current symbol',
            value: trackedJob.currentSymbol ?? 'Waiting for first symbol',
          },
          {
            label: 'Symbol cursor',
            value: `${currentSymbolIndex} / ${trackedJob.totalSymbols}`,
          },
          {
            label: 'Rows imported',
            value: formatNumber(trackedJob.importedRowCount),
          },
          {
            label: 'Current chunk start',
            value: formatOptionalDateTime(trackedJob.currentChunkStart),
          },
          {
            label: 'Last backend update',
            value: formatRelativeUpdate(trackedJob.updatedAt),
          },
          {
            label: 'Last pushed event',
            value: formatLiveImportEventTimestamp(lastImportEventAt),
          },
          {
            label: 'Retry windows',
            value: `${trackedJob.retryCount ?? trackedJob.asyncMonitor?.attemptCount ?? 0} / ${
              trackedJob.maxRetryCount ?? trackedJob.asyncMonitor?.maxAttempts ?? 'n/a'
            }`,
          },
        ]}
      />
      <Typography variant="body2" color="text.secondary">
        {trackedJob.statusMessage}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Attempts: {trackedJob.attemptCount} | Started:{' '}
        {formatOptionalDateTime(trackedJob.startedAt)}
        {trackedJob.nextRetryAt
          ? ` | Next retry: ${formatOptionalDateTime(trackedJob.nextRetryAt)}`
          : ''}
        {trackedJob.completedAt
          ? ` | Completed: ${formatOptionalDateTime(trackedJob.completedAt)}`
          : ''}
      </Typography>
      {(trackedJob.status === 'RUNNING' ||
        trackedJob.status === 'QUEUED' ||
        trackedJob.status === 'WAITING_RETRY') &&
      !transportConnected ? (
        <Alert severity="warning">
          Live stream is not connected, so this page is temporarily relying on polling for import
          updates.
        </Alert>
      ) : null}
      {trackedJob.asyncMonitor?.timedOut ? (
        <Alert severity="error">
          This job has exceeded the expected backend update window. Review the provider state and
          retry manually if it does not recover.
        </Alert>
      ) : null}
    </SurfacePanel>
  );
}

interface MarketDataJobFormPanelProps {
  providers: MarketDataProvider[];
  selectedProvider: MarketDataProvider | null;
  form: MarketDataFormState;
  effectiveAssetType: MarketDataFormState['assetType'];
  effectiveTimeframe: string;
  adjustedEnabled: boolean;
  regularSessionEnabled: boolean;
  isCreating: boolean;
  onProviderChange: (providerId: string) => void;
  onAssetTypeChange: (assetType: string) => void;
  onTimeframeChange: (timeframe: string) => void;
  onSymbolsChange: (symbolsText: string) => void;
  onStartDateChange: (startDate: string) => void;
  onEndDateChange: (endDate: string) => void;
  onDatasetNameChange: (datasetName: string) => void;
  onAdjustedChange: (adjusted: boolean) => void;
  onRegularSessionOnlyChange: (regularSessionOnly: boolean) => void;
  onSubmit: () => void | Promise<void>;
}

export function MarketDataJobFormPanel({
  providers,
  selectedProvider,
  form,
  effectiveAssetType,
  effectiveTimeframe,
  adjustedEnabled,
  regularSessionEnabled,
  isCreating,
  onProviderChange,
  onAssetTypeChange,
  onTimeframeChange,
  onSymbolsChange,
  onStartDateChange,
  onEndDateChange,
  onDatasetNameChange,
  onAdjustedChange,
  onRegularSessionOnlyChange,
  onSubmit,
}: MarketDataJobFormPanelProps) {
  return (
    <SurfacePanel
      title="Step 2. Define import scope"
      description="Choose symbols, timeframe, and dataset naming before the downloader queues the job."
      sx={{ height: '100%' }}
    >
      <Stack spacing={2}>
          <FieldTooltip title="Choose the provider that will supply the historical bars. The page shows the account or API-key requirement for the currently selected source.">
            <FormControl fullWidth>
              <InputLabel id="market-data-provider-label">Provider</InputLabel>
              <Select
                labelId="market-data-provider-label"
                value={selectedProvider?.id ?? ''}
                label="Provider"
                onChange={(event) => onProviderChange(event.target.value)}
              >
                {providers.map((provider) => (
                  <MenuItem key={provider.id} value={provider.id}>
                    {provider.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </FieldTooltip>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <FormControl fullWidth>
                <InputLabel id="market-data-asset-type-label">Asset Type</InputLabel>
                <Select
                  labelId="market-data-asset-type-label"
                  value={effectiveAssetType}
                  label="Asset Type"
                  onChange={(event) => onAssetTypeChange(event.target.value)}
                >
                  {(selectedProvider?.supportedAssetTypes ?? []).map((assetType) => (
                    <MenuItem key={assetType} value={assetType}>
                      {assetType}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <FormControl fullWidth>
                <InputLabel id="market-data-timeframe-label">Timeframe</InputLabel>
                <Select
                  labelId="market-data-timeframe-label"
                  value={effectiveTimeframe}
                  label="Timeframe"
                  onChange={(event) => onTimeframeChange(event.target.value)}
                >
                  {(selectedProvider?.supportedTimeframes ?? []).map((timeframe) => (
                    <MenuItem key={timeframe} value={timeframe}>
                      {timeframe}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
          </Grid>

          <FieldTooltip title="Provide one symbol per line or a comma-separated list. Use stock tickers for equities and provider-compatible spot pairs for crypto, for example BTC/USDT or BTC/USD.">
            <TextField
              label="Symbols"
              multiline
              minRows={4}
              value={form.symbolsText}
              onChange={(event) => onSymbolsChange(event.target.value.replace(/\r\n/g, '\n'))}
              helperText={
                selectedProvider
                  ? `Examples: ${selectedProvider.symbolExamples.join(', ')}`
                  : 'One symbol per line.'
              }
            />
          </FieldTooltip>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                type="date"
                label="Start Date"
                value={form.startDate}
                onChange={(event) => onStartDateChange(event.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                type="date"
                label="End Date"
                value={form.endDate}
                onChange={(event) => onEndDateChange(event.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
          </Grid>

          <TextField
            label="Dataset Name (optional)"
            value={form.datasetName}
            onChange={(event) => onDatasetNameChange(event.target.value)}
            placeholder="Binance BTC majors 1h 2024-2026"
            helperText="Leave blank to let the app generate a readable dataset name."
          />

          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <FormControlLabel
              control={
                <Switch
                  checked={adjustedEnabled && form.adjusted}
                  disabled={!adjustedEnabled}
                  onChange={(event) => onAdjustedChange(event.target.checked)}
                />
              }
              label="Adjusted bars"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={regularSessionEnabled && form.regularSessionOnly}
                  disabled={!regularSessionEnabled}
                  onChange={(event) => onRegularSessionOnlyChange(event.target.checked)}
                />
              }
              label="Regular session only"
            />
          </Stack>

          <Button
            variant="contained"
            startIcon={<CloudDownloadOutlinedIcon />}
            onClick={() => void onSubmit()}
            disabled={isCreating || !selectedProvider}
          >
            Create Download Job
          </Button>
      </Stack>
    </SurfacePanel>
  );
}

interface MarketDataProviderSetupPanelProps {
  selectedProvider: MarketDataProvider | null;
}

export function MarketDataProviderSetupPanel({
  selectedProvider,
}: MarketDataProviderSetupPanelProps) {
  return (
    <SurfacePanel
      title="Step 1. Pick provider"
      description="Review credentials, supported assets, and provider notes before you start the import."
    >
      <Stack spacing={1.5}>
          {selectedProvider ? (
            <>
              <Typography variant="body2" color="text.secondary">
                {selectedProvider.description}
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {selectedProvider.supportedAssetTypes.map((assetType) => (
                  <Chip key={assetType} label={assetType} size="small" />
                ))}
                {selectedProvider.supportedTimeframes.map((timeframe) => (
                  <Chip key={timeframe} label={timeframe} size="small" variant="outlined" />
                ))}
              </Stack>
              <Alert
                severity={
                  selectedProvider.apiKeyRequired && !selectedProvider.apiKeyConfigured
                    ? 'warning'
                    : 'info'
                }
              >
                {selectedProvider.apiKeyRequired ? (
                  <>
                    {providerCredentialMessage(
                      selectedProvider.apiKeyConfiguredSource,
                      selectedProvider.apiKeyEnvironmentVariable
                    )}
                  </>
                ) : (
                  <>This provider works without an API key.</>
                )}
              </Alert>
              <Typography variant="body2">{selectedProvider.accountNotes}</Typography>
              <Stack direction="row" spacing={1}>
                <Button
                  component={Link}
                  href={selectedProvider.docsUrl}
                  target="_blank"
                  rel="noreferrer"
                  endIcon={<OpenInNewIcon />}
                  size="small"
                >
                  Docs
                </Button>
                <Button
                  component={Link}
                  href={selectedProvider.signupUrl}
                  target="_blank"
                  rel="noreferrer"
                  endIcon={<OpenInNewIcon />}
                  size="small"
                >
                  Account / Key
                </Button>
              </Stack>
            </>
          ) : (
            <EmptyState
              title="Loading provider metadata"
              description="Provider capabilities and account requirements will appear here."
              tone="info"
            />
          )}
      </Stack>
    </SurfacePanel>
  );
}

export function MarketDataHowToPanel() {
  return (
    <SurfacePanel
      title="Step 3. Move finished data into research"
      description="Imports become useful only after they land in Backtest with clear provenance."
    >
      <Stack spacing={1.5}>
          <Typography variant="body2" color="text.secondary">
            1. Configure any required provider API key in your environment.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            2. Create a job here. The backend worker fetches bars in chunks and keeps retrying
            automatically when a provider asks it to wait.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            3. When a job completes, its dataset is available in the Backtest page.
          </Typography>
          <Button
            component={RouterLink}
            to="/backtest"
            size="small"
            startIcon={<PlayArrowOutlinedIcon />}
          >
            Open Backtest
          </Button>
      </Stack>
    </SurfacePanel>
  );
}

interface MarketDataJobsPanelProps {
  jobs: MarketDataImportJob[];
  activeJobCount: number;
  waitingJobCount: number;
  jobStateUpdating: boolean;
  onRetry: (jobId: number) => void | Promise<void>;
  onCancel: (jobId: number) => void | Promise<void>;
}

export function MarketDataJobsPanel({
  jobs,
  activeJobCount,
  waitingJobCount,
  jobStateUpdating,
  onRetry,
  onCancel,
}: MarketDataJobsPanelProps) {
  return (
    <SurfacePanel
      title="Step 3. Jobs and output"
      description={`Active jobs: ${activeJobCount} | Waiting to retry: ${waitingJobCount}`}
      actions={
        jobStateUpdating ? <Chip label="Updating job state" color="warning" size="small" /> : undefined
      }
    >
      <Stack spacing={2}>

          {jobs.length === 0 ? (
            <EmptyState
              title="No import jobs yet"
              description="Create one above to start building datasets automatically."
              tone="info"
            />
          ) : (
            <Grid container spacing={2}>
              {jobs.map((job) => (
                <Grid key={job.id} size={{ xs: 12, lg: 6 }}>
                  <SurfacePanel
                    title={`#${job.id} ${job.datasetName}`}
                    description={`${job.providerLabel} | ${job.assetType} | ${job.symbolsCsv} | ${job.timeframe}`}
                    sx={{ height: '100%' }}
                    actions={<Chip size="small" color={statusColor(job.status)} label={job.status} />}
                  >
                    <Stack spacing={1.5}>
                      <Typography variant="body2" color="text.secondary">
                        {job.startDate} to {job.endDate}
                      </Typography>
                      <Typography variant="body2">{job.statusMessage}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        Rows imported: {formatNumber(job.importedRowCount)} | Attempts:{' '}
                        {job.attemptCount}
                        {job.retryCount !== undefined && job.maxRetryCount !== undefined
                          ? ` | Retry windows: ${job.retryCount} / ${job.maxRetryCount}`
                          : ''}
                        {job.currentSymbol ? ` | Current symbol: ${job.currentSymbol}` : ''}
                        {job.currentChunkStart
                          ? ` | Chunk start: ${formatOptionalDateTime(job.currentChunkStart)}`
                          : ''}
                        {job.nextRetryAt
                          ? ` | Next retry: ${formatOptionalDateTime(job.nextRetryAt)}`
                          : ''}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Last backend update: {formatRelativeUpdate(job.updatedAt)} | Started:{' '}
                        {job.startedAt ? formatOptionalDateTime(job.startedAt) : 'queued only'}
                        {job.completedAt
                          ? ` | Completed: ${formatOptionalDateTime(job.completedAt)}`
                          : ''}
                        {job.asyncMonitor?.timedOut ? ' | Timeout window exceeded' : ''}
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        {job.status === 'FAILED' || job.status === 'CANCELLED' ? (
                          <Button
                            size="small"
                            startIcon={<RefreshIcon />}
                            onClick={() => void onRetry(job.id)}
                          >
                            Retry
                          </Button>
                        ) : null}
                        {job.status === 'QUEUED' ||
                        job.status === 'RUNNING' ||
                        job.status === 'WAITING_RETRY' ? (
                          <Button
                            size="small"
                            color="inherit"
                            startIcon={<CancelOutlinedIcon />}
                            onClick={() => void onCancel(job.id)}
                          >
                            Cancel
                          </Button>
                        ) : null}
                        {job.datasetReady && job.datasetId ? (
                          <Button
                            size="small"
                            component={RouterLink}
                            to="/backtest"
                            startIcon={<PlayArrowOutlinedIcon />}
                          >
                            Dataset #{job.datasetId} ready
                          </Button>
                        ) : null}
                      </Stack>
                    </Stack>
                  </SurfacePanel>
                </Grid>
              ))}
            </Grid>
          )}
      </Stack>
    </SurfacePanel>
  );
}
