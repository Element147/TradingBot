import { Alert, Button, Chip, Grid, Stack, Tab, Tabs } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import {
  useArchiveBacktestDatasetMutation,
  useDeleteBacktestMutation,
  useGetBacktestAlgorithmsQuery,
  useGetBacktestDatasetRetentionReportQuery,
  useGetBacktestDatasetsQuery,
  useGetBacktestDetailsQuery,
  useGetBacktestExperimentSummariesQuery,
  useGetBacktestSummaryQuery,
  useGetBacktestsQuery,
  useLazyCompareBacktestsQuery,
  useReplayBacktestMutation,
  useRestoreBacktestDatasetMutation,
  useRunBacktestMutation,
  useUploadBacktestDatasetMutation,
  type BacktestDataset,
  type BacktestExecutionStatus,
  type BacktestHistoryItem,
  type BacktestHistoryQuery,
  type BacktestHistorySortField,
  type BacktestValidationStatus,
  type RunBacktestPayload,
} from './backtestApi';
import { BacktestConfigModal } from './BacktestConfigModal';
import { BacktestDatasetInventoryPanel } from './BacktestDatasetInventoryPanel';
import { BacktestHistoryWorkspacePanel } from './BacktestHistoryWorkspacePanel';
import {
  initialBacktestForm,
  isExecutionActive,
  parseSymbols,
  resolveFormState,
} from './backtestPageState';
import {
  BacktestExperimentSummariesPanel,
  BacktestRunLauncherPanel,
  BacktestTrackedRunCard,
  BacktestTransportStatusAlert,
} from './BacktestPanels';
import { BacktestResults } from './BacktestResults';

import { useAppSelector } from '@/app/hooks';
import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
  PageSectionHeader,
} from '@/components/layout/PageContent';
import { useInteractiveTableState } from '@/components/ui/InteractiveTable';
import { StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import { executionContextMeta } from '@/features/execution/executionContext';
import { getStrategyProfile } from '@/features/strategies/strategyProfiles';
import {
  selectConnectionError,
  selectIsConnected,
  selectLastEventTimeForType,
  selectSubscribedChannels,
} from '@/features/websocket/websocketSlice';
import axiosClient, { getErrorMessage } from '@/services/axiosClient';
import { sanitizeText } from '@/utils/security';

type BacktestRouteTab = 'review' | 'runs' | 'datasets' | 'history';

const routeTabs: BacktestRouteTab[] = ['review', 'runs', 'datasets', 'history'];

const parseBacktestIdParam = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
};

const parseBacktestIdListParam = (value: string | null) =>
  value
    ?.split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0) ?? [];

const parseRouteTab = (value: string | null, fallback: BacktestRouteTab): BacktestRouteTab =>
  routeTabs.find((tab) => tab === value) ?? fallback;

export default function BacktestPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const websocketConnected = useAppSelector(selectIsConnected);
  const websocketError = useAppSelector(selectConnectionError);
  const subscribedChannels = useAppSelector(selectSubscribedChannels);
  const lastBacktestEventAt = useAppSelector((state) =>
    selectLastEventTimeForType(state, 'backtest.progress')
  );
  const routeExecutionContext = executionContextMeta.research;
  const backtestLiveTransportConnected =
    websocketConnected && subscribedChannels.includes(`${routeExecutionContext.environment}.backtests`);
  const backtestPollingInterval = backtestLiveTransportConnected ? 30000 : 2000;

  const [form, setForm] = useState(initialBacktestForm);
  const [selectedId, setSelectedId] = useState<number | null>(() =>
    parseBacktestIdParam(searchParams.get('run'))
  );
  const [datasetName, setDatasetName] = useState('');
  const [datasetFile, setDatasetFile] = useState<File | null>(null);
  const [feedback, setFeedback] = useState<{ severity: 'success' | 'error'; message: string } | null>(
    null
  );
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [comparisonIds, setComparisonIds] = useState<number[]>(() =>
    parseBacktestIdListParam(searchParams.get('compare'))
  );
  const [activeComparisonIds, setActiveComparisonIds] = useState<number[]>([]);
  const [historyRangeFilters, setHistoryRangeFilters] = useState({
    feesBpsMin: '',
    feesBpsMax: '',
    slippageBpsMin: '',
    slippageBpsMax: '',
  });
  const historyTableStateControls = useInteractiveTableState({
    tableId: 'backtest-history',
    initialPageSize: 25,
  });

  const { data: algorithms = [] } = useGetBacktestAlgorithmsQuery();
  const { data: datasets = [] } = useGetBacktestDatasetsQuery();
  const { data: retentionReport } = useGetBacktestDatasetRetentionReportQuery();
  const { data: experimentSummaries = [] } = useGetBacktestExperimentSummariesQuery();
  const historyQuery = useMemo<BacktestHistoryQuery>(() => {
    const activeSorting = historyTableStateControls.state.sorting[0];
    const columnFilterValue = (columnId: string) => {
      const filter = historyTableStateControls.state.columnFilters.find(
        (entry) => entry.id === columnId
      );
      return typeof filter?.value === 'string' && filter.value.trim() ? filter.value.trim() : undefined;
    };
    const parseOptionalNumber = (value: string) => {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : undefined;
    };

    return {
      page: historyTableStateControls.state.pagination.pageIndex + 1,
      pageSize: historyTableStateControls.state.pagination.pageSize,
      sortBy: activeSorting?.id as BacktestHistorySortField | undefined,
      sortDirection: activeSorting?.desc ? 'desc' : activeSorting ? 'asc' : undefined,
      search: historyTableStateControls.state.globalFilter.trim() || undefined,
      strategyId: columnFilterValue('strategyId'),
      datasetName: columnFilterValue('datasetName'),
      experimentName: columnFilterValue('experimentName'),
      market: columnFilterValue('market'),
      executionStatus: columnFilterValue('executionStatus') as BacktestExecutionStatus | undefined,
      validationStatus: columnFilterValue('validationStatus') as
        | BacktestValidationStatus
        | undefined,
      feesBpsMin: parseOptionalNumber(historyRangeFilters.feesBpsMin),
      feesBpsMax: parseOptionalNumber(historyRangeFilters.feesBpsMax),
      slippageBpsMin: parseOptionalNumber(historyRangeFilters.slippageBpsMin),
      slippageBpsMax: parseOptionalNumber(historyRangeFilters.slippageBpsMax),
    };
  }, [historyRangeFilters, historyTableStateControls.state]);
  const {
    data: history,
    isLoading,
    isError,
  } = useGetBacktestsQuery(historyQuery, {
    pollingInterval: backtestPollingInterval,
    skipPollingIfUnfocused: true,
  });
  const historyItems = useMemo(() => history?.items ?? [], [history?.items]);
  const activeDatasets = useMemo(
    () => datasets.filter((dataset) => !dataset.archived),
    [datasets]
  );
  const resolvedForm = useMemo(
    () => resolveFormState(form, activeDatasets, algorithms),
    [activeDatasets, algorithms, form]
  );

  const [uploadDataset, { isLoading: isUploading }] = useUploadBacktestDatasetMutation();
  const [archiveDataset, { isLoading: isArchivingDataset }] = useArchiveBacktestDatasetMutation();
  const [restoreDataset, { isLoading: isRestoringDataset }] = useRestoreBacktestDatasetMutation();
  const [runBacktest, { isLoading: isRunning }] = useRunBacktestMutation();
  const [replayBacktest, { isLoading: isReplaying }] = useReplayBacktestMutation();
  const [deleteBacktest, { isLoading: isDeletingBacktest }] = useDeleteBacktestMutation();
  const [loadComparison, { data: comparison, isFetching: isComparing, error: comparisonError }] =
    useLazyCompareBacktestsQuery();

  const selectedAlgorithm = useMemo(
    () => algorithms.find((algorithm) => algorithm.id === resolvedForm.algorithmType) ?? null,
    [algorithms, resolvedForm.algorithmType]
  );
  const selectedAlgorithmProfile = useMemo(
    () => getStrategyProfile(resolvedForm.algorithmType),
    [resolvedForm.algorithmType]
  );
  const requiresDatasetUniverse = selectedAlgorithm?.selectionMode === 'DATASET_UNIVERSE';
  const fallbackTrackedRun = useMemo(
    () => historyItems.find((item) => isExecutionActive(item)) ?? historyItems[0] ?? null,
    [historyItems]
  );
  const effectiveSelectedId = selectedId ?? fallbackTrackedRun?.id ?? null;
  const trackedRun = useMemo(
    () => historyItems.find((item) => item.id === effectiveSelectedId) ?? null,
    [effectiveSelectedId, historyItems]
  );
  const selectedRunIsActive = trackedRun ? isExecutionActive(trackedRun) : false;
  const defaultRouteTab: BacktestRouteTab = effectiveSelectedId !== null ? 'review' : 'runs';
  const activeRouteTab = parseRouteTab(searchParams.get('tab'), defaultRouteTab);

  const { data: details, refetch: refetchDetails } = useGetBacktestDetailsQuery(
    effectiveSelectedId ?? 0,
    {
      skip: effectiveSelectedId === null || selectedRunIsActive,
      pollingInterval: 0,
      skipPollingIfUnfocused: true,
    }
  );
  const { data: activeRunSummary } = useGetBacktestSummaryQuery(effectiveSelectedId ?? 0, {
    skip: effectiveSelectedId === null || !selectedRunIsActive,
    pollingInterval: backtestPollingInterval,
    skipPollingIfUnfocused: true,
  });

  useEffect(() => {
    if (effectiveSelectedId !== null && !selectedRunIsActive) {
      void refetchDetails();
    }
  }, [effectiveSelectedId, refetchDetails, selectedRunIsActive]);

  useEffect(() => {
    const nextParams = new URLSearchParams(searchParams);

    if (selectedId) {
      nextParams.set('run', String(selectedId));
    } else if (fallbackTrackedRun?.id) {
      nextParams.set('run', String(fallbackTrackedRun.id));
    } else {
      nextParams.delete('run');
    }

    if (comparisonIds.length > 0) {
      nextParams.set('compare', comparisonIds.join(','));
    } else {
      nextParams.delete('compare');
    }

    nextParams.set('tab', activeRouteTab);

    if (nextParams.toString() !== searchParams.toString()) {
      setSearchParams(nextParams, { replace: true });
    }
  }, [
    activeRouteTab,
    comparisonIds,
    fallbackTrackedRun?.id,
    searchParams,
    selectedId,
    setSearchParams,
  ]);

  const updateSearchParams = (update: (params: URLSearchParams) => void) => {
    const nextParams = new URLSearchParams(searchParams);
    update(nextParams);
    setSearchParams(nextParams, { replace: true });
  };

  const setRouteTab = (tab: BacktestRouteTab) => {
    updateSearchParams((params) => {
      params.set('tab', tab);
    });
  };

  const comparisonIsStale =
    activeComparisonIds.length > 0 && activeComparisonIds.join(',') !== comparisonIds.join(',');
  const comparisonErrorMessage = comparisonError ? getErrorMessage(comparisonError) : null;
  const datasetLifecycleBusy = isArchivingDataset || isRestoringDataset;
  const summaryItems = useMemo<PageMetricItem[]>(
    () => [
      {
        label: 'Active Datasets',
        value: activeDatasets.length.toString(),
        detail: `${datasets.length} total datasets in the catalog.`,
        tone: activeDatasets.length > 0 ? 'success' : 'warning',
      },
      {
        label: 'Algorithm Catalog',
        value: algorithms.length.toString(),
        detail: 'Canonical strategy IDs stay aligned with the backtest registry.',
        tone: 'info',
      },
      {
        label: 'Transport',
        value: backtestLiveTransportConnected ? 'Live stream' : 'Polling fallback',
        detail: backtestLiveTransportConnected
          ? 'Progress arrives through the authenticated WebSocket channel.'
          : 'Fallback polling keeps run progress visible when the live stream is unavailable.',
        tone: backtestLiveTransportConnected ? 'success' : 'warning',
      },
      {
        label: 'Tracked Run',
        value: trackedRun ? `#${trackedRun.id}` : 'None selected',
        detail: trackedRun
          ? `${trackedRun.executionStatus} on ${trackedRun.datasetName ?? 'dataset'}`
          : 'Select a run from history to inspect detailed metrics and charts.',
        tone: trackedRun ? 'info' : 'default',
      },
    ],
    [
      activeDatasets.length,
      algorithms.length,
      backtestLiveTransportConnected,
      datasets.length,
      trackedRun,
    ]
  );

  const onUploadDataset = async () => {
    if (!datasetFile) {
      setFeedback({ severity: 'error', message: 'Choose a CSV file first.' });
      return;
    }

    try {
      const uploaded = await uploadDataset({
        file: datasetFile,
        name: datasetName.trim() || undefined,
      }).unwrap();

      const uploadedSymbols = parseSymbols(uploaded.symbolsCsv);
      setForm((prev) => ({
        ...prev,
        datasetId: String(uploaded.id),
        symbol: uploadedSymbols[0] ?? prev.symbol,
      }));
      setFeedback({
        severity: 'success',
        message: `Dataset '${uploaded.name}' uploaded (${uploaded.rowCount} rows) and added to the active run catalog.`,
      });
      setDatasetFile(null);
      setDatasetName('');
      setRouteTab('datasets');
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const onArchiveDataset = async (dataset: BacktestDataset) => {
    try {
      await archiveDataset({
        datasetId: dataset.id,
        reason: dataset.usedByBacktests
          ? 'Hidden from new-run selection while retained for replay reproducibility.'
          : 'Archived from active inventory after lifecycle review.',
      }).unwrap();
      setFeedback({
        severity: 'success',
        message: `Archived dataset '${dataset.name}'. It remains available for download and replay-backed research.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const onRestoreDataset = async (datasetId: number) => {
    try {
      const restored = await restoreDataset(datasetId).unwrap();
      setFeedback({
        severity: 'success',
        message: `Restored dataset '${restored.name}' to the active run catalog.`,
      });
      setRouteTab('datasets');
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const onRunBacktest = async (payload: RunBacktestPayload) => {
    try {
      const response = await runBacktest(payload).unwrap();
      setSelectedId(response.id);
      setFeedback({
        severity: 'success',
        message: `Backtest ${response.id} submitted (${response.status}).`,
      });
      setConfigModalOpen(false);
      setRouteTab('review');
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const toggleComparison = (backtestId: number) => {
    setComparisonIds((prev) =>
      prev.includes(backtestId) ? prev.filter((id) => id !== backtestId) : [...prev, backtestId]
    );
  };

  const onReplayBacktest = async (backtestId: number) => {
    try {
      const replayed = await replayBacktest(backtestId).unwrap();
      setSelectedId(replayed.id);
      setFeedback({
        severity: 'success',
        message: `Replay started from run ${backtestId}. New run: ${replayed.id} (${replayed.status}).`,
      });
      setRouteTab('review');
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const onViewDetails = (backtestId: number) => {
    setSelectedId(backtestId);
    setRouteTab('review');
    setFeedback({
      severity: 'success',
      message: `Showing detailed results for run ${backtestId}.`,
    });
  };

  const onDeleteResult = async (item: BacktestHistoryItem) => {
    if (isExecutionActive(item)) {
      setFeedback({
        severity: 'error',
        message: `Run ${item.id} is still active and cannot be deleted yet.`,
      });
      return;
    }

    if (
      !window.confirm(
        `Delete backtest result #${item.id}? This also removes its stored equity curve and trade series.`
      )
    ) {
      return;
    }

    try {
      await deleteBacktest(item.id).unwrap();
      setComparisonIds((prev) => prev.filter((id) => id !== item.id));
      setActiveComparisonIds((prev) => prev.filter((id) => id !== item.id));
      if (selectedId === item.id) {
        setSelectedId(null);
      }
      setFeedback({
        severity: 'success',
        message: `Deleted backtest result ${item.id}.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const onCompareSelected = async () => {
    if (comparisonIds.length < 2) {
      setFeedback({ severity: 'error', message: 'Select at least two backtests to compare.' });
      return;
    }

    try {
      await loadComparison(comparisonIds).unwrap();
      setActiveComparisonIds(comparisonIds);
      setFeedback({
        severity: 'success',
        message: `Comparison loaded for runs ${comparisonIds.map((id) => `#${id}`).join(', ')}.`,
      });
      setRouteTab('history');
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const onDownloadDataset = async (datasetId: number) => {
    try {
      const response = await axiosClient.get<Blob>(`/api/backtests/datasets/${datasetId}/download`, {
        responseType: 'blob',
      });
      const dispositionHeader = response.headers['content-disposition'];
      const disposition = typeof dispositionHeader === 'string' ? dispositionHeader : '';
      const match = /filename="?([^"]+)"?/i.exec(disposition);
      const filename = match?.[1] ?? `dataset-${datasetId}.csv`;
      const objectUrl = URL.createObjectURL(response.data);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(objectUrl);

      const checksum = response.headers['x-dataset-checksum-sha256'];
      const schemaVersion = response.headers['x-dataset-schema-version'];
      setFeedback({
        severity: 'success',
        message: `Downloaded ${filename}${checksum ? ` (${schemaVersion}, checksum ${checksum.slice(0, 12)}...)` : ''}.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getErrorMessage(error) });
    }
  };

  const renderReviewTab = () => {
    if (details) {
      return (
        <BacktestResults
          details={details}
          transportLabel={backtestLiveTransportConnected ? 'Live WebSocket stream' : 'Fallback polling'}
          lastLiveEventAt={lastBacktestEventAt}
          transportError={websocketError}
          onReplay={() => void onReplayBacktest(details.id)}
          onFocusHistory={() => setRouteTab('history')}
          onDelete={() => void onDeleteResult(details)}
          deleteDisabled={isExecutionActive(details) || isDeletingBacktest}
        />
      );
    }

    if (activeRunSummary ?? trackedRun) {
      return (
        <BacktestTrackedRunCard
          trackedRun={activeRunSummary ?? trackedRun!}
          transportConnected={backtestLiveTransportConnected}
          lastLiveEventAt={lastBacktestEventAt}
        />
      );
    }

    return (
      <SurfacePanel
        title="No run selected"
        description="Open a past run from history or launch a new one to populate the review workspace."
        tone="info"
        actions={
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Button variant="outlined" onClick={() => setRouteTab('history')}>
              Open history
            </Button>
            <Button variant="contained" onClick={() => setRouteTab('runs')}>
              Go to runs
            </Button>
          </Stack>
        }
      >
        <Alert severity="info">
          Selected-run review stays separate from setup and dataset management so the evidence pane
          can stay focused once you are ready to inspect a result.
        </Alert>
      </SurfacePanel>
    );
  };

  const renderRunsTab = () => (
    <Grid container spacing={2.5}>
      {fallbackTrackedRun && isExecutionActive(fallbackTrackedRun) ? (
        <Grid size={{ xs: 12 }}>
          <BacktestTrackedRunCard
            trackedRun={fallbackTrackedRun}
            transportConnected={backtestLiveTransportConnected}
            lastLiveEventAt={lastBacktestEventAt}
          />
        </Grid>
      ) : null}
      <Grid size={{ xs: 12, xl: 5 }}>
        <BacktestRunLauncherPanel
          selectedAlgorithm={selectedAlgorithm}
          selectedAlgorithmProfile={selectedAlgorithmProfile}
          requiresDatasetUniverse={requiresDatasetUniverse}
          hasActiveDatasets={activeDatasets.length > 0}
          onOpenConfigModal={() => setConfigModalOpen(true)}
        />
      </Grid>
      <Grid size={{ xs: 12, xl: 7 }}>
        <BacktestExperimentSummariesPanel experimentSummaries={experimentSummaries} />
      </Grid>
    </Grid>
  );

  const renderDatasetsTab = () => (
    <Grid container spacing={2.5}>
      <Grid size={{ xs: 12 }}>
        <BacktestDatasetInventoryPanel
          datasetName={datasetName}
          datasetFile={datasetFile}
          retentionReport={retentionReport}
          datasets={datasets}
          hasActiveDatasets={activeDatasets.length > 0}
          isUploading={isUploading}
          datasetLifecycleBusy={datasetLifecycleBusy}
          onDatasetNameChange={(value) => setDatasetName(sanitizeText(value))}
          onDatasetFileChange={(file) => setDatasetFile(file)}
          onUploadDataset={() => void onUploadDataset()}
          onDownloadDataset={onDownloadDataset}
          onArchiveDataset={onArchiveDataset}
          onRestoreDataset={onRestoreDataset}
        />
      </Grid>
    </Grid>
  );

  const renderHistoryTab = () => (
    <BacktestHistoryWorkspacePanel
      history={history ?? { items: [], total: 0, page: 1, pageSize: historyQuery.pageSize ?? 25 }}
      comparison={comparison}
      comparisonIds={comparisonIds}
      selectedId={effectiveSelectedId}
      comparisonIsStale={comparisonIsStale}
      comparisonErrorMessage={comparisonErrorMessage}
      lastLiveEventAt={lastBacktestEventAt}
      isLoading={isLoading}
      isError={isError}
      isComparing={isComparing}
      isReplaying={isReplaying}
      isDeletingBacktest={isDeletingBacktest}
      tableStateControls={historyTableStateControls}
      rangeFilters={historyRangeFilters}
      onRangeFilterChange={(field, value) =>
        setHistoryRangeFilters((current) => ({ ...current, [field]: value }))
      }
      onCompareSelected={onCompareSelected}
      onClearComparison={() => {
        setComparisonIds([]);
        setActiveComparisonIds([]);
      }}
      onToggleComparison={toggleComparison}
      onSelectRun={setSelectedId}
      onViewDetails={onViewDetails}
      onReplayBacktest={onReplayBacktest}
      onDeleteResult={onDeleteResult}
    />
  );

  const tabDescriptions: Record<BacktestRouteTab, { title: string; description: string }> = {
    review: {
      title: 'Selected run review',
      description:
        'Keep the selected run and its inner evidence tabs isolated from setup, dataset lifecycle, and full history work.',
    },
    runs: {
      title: 'Run setup',
      description:
        'Launch new backtests and review lighter experiment rollups before moving into the heavier evidence workspace.',
    },
    datasets: {
      title: 'Dataset lifecycle',
      description:
        'Upload, audit, sort, and restore datasets in one place so data preparation stops competing with run review.',
    },
    history: {
      title: 'History and comparison',
      description:
        'Browse sortable run history, load comparisons, and jump into a detailed review only when you need it.',
    },
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Research-only workflow"
          description="Upload or restore datasets, launch runs with realistic costs, then compare and inspect results without blurring the line between research and live execution."
          actions={
            <>
              <Button
                variant="contained"
                onClick={() => setConfigModalOpen(true)}
                disabled={!activeDatasets.length}
              >
                Run new backtest
              </Button>
              <Button variant="outlined" onClick={() => setRouteTab('history')}>
                Open history
              </Button>
            </>
          }
          chips={
            <>
              <Chip label="Backtests stay research-only" variant="outlined" />
              <Chip label="Dataset provenance stays visible" variant="outlined" />
              <Chip label="Costs and slippage remain explicit" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={summaryItems} />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        <SurfacePanel
          title="Research posture"
          description="Keep route posture, transport behavior, and the current evidence path visible without adding another loose alert stack."
          tone="info"
          actions={
            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
              <StatusPill
                label={`Context: ${routeExecutionContext.label}`}
                tone="info"
                variant="filled"
              />
              <StatusPill
                label={backtestLiveTransportConnected ? 'Live stream connected' : 'Polling fallback'}
                tone={backtestLiveTransportConnected ? 'success' : 'warning'}
              />
            </Stack>
          }
        >
          <Alert severity="info">
            This workspace owns the `{routeExecutionContext.label.toLowerCase()}` execution
            context, so backtests and dataset operations stay pinned to test-scoped telemetry even
            when operational pages are reviewing live-readiness elsewhere.
          </Alert>
          <BacktestTransportStatusAlert
            transportConnected={backtestLiveTransportConnected}
            lastLiveEventAt={lastBacktestEventAt}
            websocketError={websocketError}
          />
        </SurfacePanel>

        <SurfacePanel
          title="Backtest tasks"
          description="Split the route by task so review, launch setup, datasets, and history each have a calmer workspace."
          actions={
            <StatusPill
              label={`Open: ${tabDescriptions[activeRouteTab].title}`}
              tone="info"
              variant="filled"
            />
          }
        >
          <Tabs
            value={activeRouteTab}
            onChange={(_, value: BacktestRouteTab) => setRouteTab(value)}
            variant="scrollable"
            allowScrollButtonsMobile
            aria-label="Backtest route sections"
          >
            <Tab value="review" label="Review" />
            <Tab value="runs" label="Runs" />
            <Tab value="datasets" label="Datasets" />
            <Tab value="history" label="History" />
          </Tabs>
        </SurfacePanel>

        <PageSectionHeader
          title={tabDescriptions[activeRouteTab].title}
          description={tabDescriptions[activeRouteTab].description}
        />

        {activeRouteTab === 'review' ? renderReviewTab() : null}
        {activeRouteTab === 'runs' ? renderRunsTab() : null}
        {activeRouteTab === 'datasets' ? renderDatasetsTab() : null}
        {activeRouteTab === 'history' ? renderHistoryTab() : null}
      </PageContent>

      <BacktestConfigModal
        open={configModalOpen}
        form={resolvedForm}
        algorithms={algorithms}
        datasets={activeDatasets}
        busy={isRunning}
        onClose={() => setConfigModalOpen(false)}
        onChange={(next) => setForm(next)}
        onRun={onRunBacktest}
      />
    </AppLayout>
  );
}
