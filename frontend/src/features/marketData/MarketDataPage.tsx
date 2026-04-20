import { Alert, Button, Chip, Grid } from '@mui/material';
import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import {
  useCancelMarketDataJobMutation,
  useCreateMarketDataJobMutation,
  useGetMarketDataJobsQuery,
  useGetMarketDataProvidersQuery,
  useRetryMarketDataJobMutation,
  type CreateMarketDataJobPayload,
} from './marketDataApi';
import {
  defaultMarketDataForm,
  resolveAssetType,
  resolveTimeframe,
  splitSymbols,
  type MarketDataFormState,
} from './marketDataPageState';
import {
  MarketDataHowToPanel,
  MarketDataJobFormPanel,
  MarketDataJobsPanel,
  MarketDataProviderSetupPanel,
  MarketDataTelemetryCard,
  MarketDataTransportAlert,
  MarketDataWaitingJobsAlert,
} from './MarketDataPanels';

import { useAppSelector } from '@/app/hooks';
import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
} from '@/components/layout/PageContent';
import { executionContextMeta } from '@/features/execution/executionContext';
import {
  selectConnectionError,
  selectIsConnected,
  selectLastEventTimeForType,
  selectSubscribedChannels,
} from '@/features/websocket/websocketSlice';
import { getApiErrorMessage } from '@/services/api';
import { sanitizeText } from '@/utils/security';

export default function MarketDataPage() {
  const websocketConnected = useAppSelector(selectIsConnected);
  const websocketError = useAppSelector(selectConnectionError);
  const subscribedChannels = useAppSelector(selectSubscribedChannels);
  const lastImportEventAt = useAppSelector((state) =>
    selectLastEventTimeForType(state, 'marketData.import.progress')
  );
  const routeExecutionContext = executionContextMeta.research;
  const marketDataLiveTransportConnected =
    websocketConnected &&
    subscribedChannels.includes(`${routeExecutionContext.environment}.marketData`);
  const marketDataPollingInterval = marketDataLiveTransportConnected ? 30000 : 5000;

  const [form, setForm] = useState<MarketDataFormState>(defaultMarketDataForm);
  const [feedback, setFeedback] = useState<{
    severity: 'success' | 'error';
    message: string;
  } | null>(null);

  const { data: providers = [] } = useGetMarketDataProvidersQuery();
  const { data: jobs = [] } = useGetMarketDataJobsQuery(undefined, {
    pollingInterval: marketDataPollingInterval,
    skipPollingIfUnfocused: true,
  });
  const [createJob, { isLoading: isCreating }] = useCreateMarketDataJobMutation();
  const [retryJob, { isLoading: isRetrying }] = useRetryMarketDataJobMutation();
  const [cancelJob, { isLoading: isCancelling }] = useCancelMarketDataJobMutation();

  const selectedProvider = useMemo(
    () => providers.find((provider) => provider.id === form.providerId) ?? providers[0] ?? null,
    [form.providerId, providers]
  );
  const effectiveAssetType = resolveAssetType(selectedProvider, form.assetType);
  const effectiveTimeframe = resolveTimeframe(selectedProvider, form.timeframe);
  const adjustedEnabled =
    Boolean(selectedProvider?.supportsAdjusted) && effectiveAssetType === 'STOCK';
  const regularSessionEnabled =
    Boolean(selectedProvider?.supportsRegularSessionOnly) &&
    effectiveAssetType === 'STOCK';

  const waitingJobs = jobs.filter((job) => job.status === 'WAITING_RETRY');
  const activeJobs = jobs.filter(
    (job) => job.status === 'RUNNING' || job.status === 'QUEUED'
  );
  const trackedJob = useMemo(
    () => activeJobs[0] ?? waitingJobs[0] ?? jobs[0] ?? null,
    [activeJobs, jobs, waitingJobs]
  );
  const summaryItems = useMemo<PageMetricItem[]>(
    () => [
      {
        label: 'Providers',
        value: providers.length.toString(),
        detail: selectedProvider
          ? `Current provider: ${selectedProvider.label}`
          : 'Choose a provider to see requirements and examples.',
        tone: 'info',
      },
      {
        label: 'Active Jobs',
        value: activeJobs.length.toString(),
        detail: `${waitingJobs.length} waiting for retry windows.`,
        tone: activeJobs.length > 0 ? 'success' : 'default',
      },
      {
        label: 'Transport',
        value: marketDataLiveTransportConnected ? 'Live stream' : 'Polling fallback',
        detail: marketDataLiveTransportConnected
          ? 'Job progress is being pushed live.'
          : 'Fallback polling remains active so imports stay visible.',
        tone: marketDataLiveTransportConnected ? 'success' : 'warning',
      },
      {
        label: 'Latest Focus',
        value: trackedJob ? `Job #${trackedJob.id}` : 'No jobs yet',
        detail: trackedJob
          ? `${trackedJob.providerLabel} on ${trackedJob.symbolsCsv}`
          : 'Create a job to begin building datasets for the backtest catalog.',
        tone: trackedJob ? 'info' : 'default',
      },
    ],
    [
      activeJobs.length,
      marketDataLiveTransportConnected,
      providers.length,
      selectedProvider,
      trackedJob,
      waitingJobs.length,
    ]
  );

  const onSubmit = async () => {
    const payload: CreateMarketDataJobPayload = {
      providerId: selectedProvider?.id ?? form.providerId,
      assetType: effectiveAssetType,
      symbols: splitSymbols(form.symbolsText),
      timeframe: effectiveTimeframe,
      startDate: form.startDate,
      endDate: form.endDate,
      datasetName: form.datasetName.trim() || undefined,
      adjusted: adjustedEnabled ? form.adjusted : false,
      regularSessionOnly: regularSessionEnabled ? form.regularSessionOnly : false,
    };

    try {
      const created = await createJob(payload).unwrap();
      setFeedback({
        severity: 'success',
        message: `Import job #${created.id} queued for ${created.providerLabel}. The downloader will keep retrying automatically if the provider asks it to wait.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const onRetry = async (jobId: number) => {
    try {
      await retryJob(jobId).unwrap();
      setFeedback({
        severity: 'success',
        message: `Job #${jobId} restarted from the beginning.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const onCancel = async (jobId: number) => {
    try {
      await cancelJob(jobId).unwrap();
      setFeedback({ severity: 'success', message: `Job #${jobId} cancelled.` });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const handleProviderChange = (providerId: string) => {
    const nextProvider = providers.find((provider) => provider.id === providerId) ?? null;
    const nextAssetType = resolveAssetType(nextProvider, form.assetType);
    const nextTimeframe = resolveTimeframe(nextProvider, form.timeframe);
    const stockOptionsEnabled = nextAssetType === 'STOCK';

    setForm((current) => ({
      ...current,
      providerId,
      assetType: nextAssetType,
      timeframe: nextTimeframe,
      adjusted:
        stockOptionsEnabled && Boolean(nextProvider?.supportsAdjusted)
          ? current.adjusted
          : false,
      regularSessionOnly:
        stockOptionsEnabled && Boolean(nextProvider?.supportsRegularSessionOnly)
          ? current.regularSessionOnly
          : false,
    }));
  };

  const handleAssetTypeChange = (assetType: string) => {
    if (assetType !== 'STOCK' && assetType !== 'CRYPTO') {
      return;
    }

    setForm((current) => ({
      ...current,
      assetType,
      adjusted: assetType === 'STOCK' ? current.adjusted : false,
      regularSessionOnly: assetType === 'STOCK' ? current.regularSessionOnly : false,
    }));
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Provider-backed imports"
          description="Create download jobs, watch retries, and move completed datasets into the backtest catalog without guesswork about provider requirements or job state."
          actions={
            <Button component={RouterLink} to="/backtest" variant="contained">
              Open backtest catalog
            </Button>
          }
          chips={
            <>
              <Chip label="1. Pick provider" variant="outlined" />
              <Chip label="2. Define import scope" variant="outlined" />
              <Chip label="3. Review jobs and output" variant="outlined" />
              <Chip label="Jobs retry automatically" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={summaryItems} />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        <Alert severity="info">
          Market-data research owns the `{routeExecutionContext.label.toLowerCase()}` execution
          context, so imports, retry telemetry, and dataset preparation stay on test-scoped
          channels instead of inheriting the operational live-view toggle.
        </Alert>

        <MarketDataTransportAlert
          transportConnected={marketDataLiveTransportConnected}
          lastImportEventAt={lastImportEventAt}
          websocketError={websocketError}
        />
        <MarketDataWaitingJobsAlert waitingJobCount={waitingJobs.length} />

        {trackedJob ? (
          <MarketDataTelemetryCard
            trackedJob={trackedJob}
            transportConnected={marketDataLiveTransportConnected}
            lastImportEventAt={lastImportEventAt}
          />
        ) : null}

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12 }}>
                <MarketDataProviderSetupPanel selectedProvider={selectedProvider} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <MarketDataHowToPanel />
              </Grid>
            </Grid>
          </Grid>

          <Grid size={{ xs: 12, lg: 8 }}>
            <MarketDataJobFormPanel
              providers={providers}
              selectedProvider={selectedProvider}
              form={form}
              effectiveAssetType={effectiveAssetType}
              effectiveTimeframe={effectiveTimeframe}
              adjustedEnabled={adjustedEnabled}
              regularSessionEnabled={regularSessionEnabled}
              isCreating={isCreating}
              onProviderChange={handleProviderChange}
              onAssetTypeChange={handleAssetTypeChange}
              onTimeframeChange={(timeframe) =>
                setForm((current) => ({ ...current, timeframe }))
              }
              onSymbolsChange={(symbolsText) =>
                setForm((current) => ({ ...current, symbolsText }))
              }
              onStartDateChange={(startDate) =>
                setForm((current) => ({ ...current, startDate }))
              }
              onEndDateChange={(endDate) =>
                setForm((current) => ({ ...current, endDate }))
              }
              onDatasetNameChange={(datasetName) =>
                setForm((current) => ({
                  ...current,
                  datasetName: sanitizeText(datasetName),
                }))
              }
              onAdjustedChange={(adjusted) =>
                setForm((current) => ({ ...current, adjusted }))
              }
              onRegularSessionOnlyChange={(regularSessionOnly) =>
                setForm((current) => ({ ...current, regularSessionOnly }))
              }
              onSubmit={onSubmit}
            />
          </Grid>

          <Grid size={{ xs: 12 }}>
            <MarketDataJobsPanel
              jobs={jobs}
              activeJobCount={activeJobs.length}
              waitingJobCount={waitingJobs.length}
              jobStateUpdating={isRetrying || isCancelling}
              onRetry={onRetry}
              onCancel={onCancel}
            />
          </Grid>
        </Grid>
      </PageContent>
    </AppLayout>
  );
}
