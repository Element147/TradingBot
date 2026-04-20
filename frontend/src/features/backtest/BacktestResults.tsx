import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import DownloadIcon from '@mui/icons-material/Download';
import ReplayIcon from '@mui/icons-material/Replay';
import TableRowsIcon from '@mui/icons-material/TableRows';
import { Alert, Box, Button, Stack, Tab, Tabs } from '@mui/material';
import { toPng } from 'html-to-image';
import { jsPDF } from 'jspdf';
import { lazy, Suspense, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import type { BacktestDetails } from './backtestApi';
import BacktestOverviewPanel from './BacktestOverviewPanel';
import { formatBacktestMarketLabel } from './backtestTypes';

import { EmptyState, RouteActionBar, StatusPill } from '@/components/ui/Workbench';
import { formatDateTime, formatDistanceToNow } from '@/utils/formatters';

interface BacktestResultsProps {
  details: BacktestDetails;
  transportLabel?: string;
  lastLiveEventAt?: string | null;
  transportError?: string | null;
  onDelete?: () => void;
  deleteDisabled?: boolean;
  onReplay?: () => void;
  onFocusHistory?: () => void;
}

type BacktestResultSection = 'overview' | 'workspace' | 'trades' | 'analytics';

const BacktestWorkspacePanel = lazy(() => import('./BacktestWorkspacePanel'));
const BacktestTradeReviewPanel = lazy(() => import('./BacktestTradeReviewPanel'));
const BacktestAnalyticsPanel = lazy(() => import('./BacktestAnalyticsPanel'));

const resultSections: BacktestResultSection[] = ['overview', 'workspace', 'trades', 'analytics'];

const parseResultSection = (value: string | null): BacktestResultSection =>
  resultSections.find((section) => section === value) ?? 'overview';

const validationTone = (status: BacktestDetails['validationStatus']) => {
  if (status === 'PASSED' || status === 'PRODUCTION_READY') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'error';
  }
  return 'warning';
};

const formatLiveEventLabel = (value: string | null | undefined) =>
  value ? formatDistanceToNow(new Date(value)) : 'No live progress event received yet';

function BacktestPanelPendingState({ message }: { message: string }) {
  return <Alert severity="info">{message}</Alert>;
}

export function BacktestResults({
  details,
  transportLabel = 'Fallback polling',
  lastLiveEventAt = null,
  transportError = null,
  onDelete,
  deleteDisabled = false,
  onReplay,
  onFocusHistory,
}: BacktestResultsProps) {
  const exportRef = useRef<HTMLDivElement | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const activeSection = parseResultSection(searchParams.get('section'));
  const selectedSymbol = searchParams.get('symbol') ?? details.symbol;
  const hasCompleteProvenance = Boolean(
    details.datasetId &&
      details.datasetChecksumSha256 &&
      details.datasetSchemaVersion &&
      details.datasetUploadedAt
  );

  const exportPdf = async () => {
    if (!hasCompleteProvenance) {
      setFeedback(
        'Report export is blocked until dataset checksum, schema version, and upload timestamp are available.'
      );
      return;
    }

    setFeedback(null);
    const doc = new jsPDF('p', 'mm', 'a4');
    doc.setFontSize(14);
    doc.text(`Backtest ${details.id}`, 10, 12);
    doc.setFontSize(11);
    doc.text(`Experiment: ${details.experimentName}`, 10, 20);
    doc.text(`Algorithm: ${details.strategyId}`, 10, 26);
    doc.text(`Dataset: ${details.datasetName ?? '-'} (#${details.datasetId ?? 'n/a'})`, 10, 32);
    doc.text(`Schema: ${details.datasetSchemaVersion}`, 10, 38);
    doc.text(`Checksum: ${details.datasetChecksumSha256}`, 10, 44);
    doc.text(`Uploaded: ${formatDateTime(details.datasetUploadedAt ?? '')}`, 10, 50);
    doc.text(`Validation: ${details.validationStatus}`, 10, 56);
    doc.text(`Fees/Slippage: ${details.feesBps} bps / ${details.slippageBps} bps`, 10, 62);
    doc.text(
      `Sharpe: ${details.sharpeRatio.toFixed(2)} | Profit Factor: ${details.profitFactor.toFixed(2)}`,
      10,
      68
    );
    doc.text(
      `Win Rate: ${details.winRate.toFixed(2)}% | Max DD: ${details.maxDrawdown.toFixed(2)}%`,
      10,
      74
    );
    doc.text(`Section: ${activeSection}`, 10, 80);

    if (exportRef.current) {
      const dataUrl = await toPng(exportRef.current, { pixelRatio: 1.3, cacheBust: true });
      doc.addImage(dataUrl, 'PNG', 10, 88, 190, 108);
    }

    doc.save(`backtest_${details.id}_${new Date().toISOString().slice(0, 10)}.pdf`);
  };

  const copyShareableLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setFeedback('Shareable run link copied to clipboard.');
    } catch {
      setFeedback('Unable to copy the shareable run link from this browser context.');
    }
  };

  const renderActivePanel = () => {
    switch (activeSection) {
      case 'workspace':
        return (
          <Suspense
            fallback={
              <BacktestPanelPendingState message="Loading the chart workspace chunk and its telemetry boundary." />
            }
          >
            <BacktestWorkspacePanel details={details} />
          </Suspense>
        );
      case 'trades':
        return (
          <Suspense
            fallback={
              <BacktestPanelPendingState message="Loading the trade review chunk and its on-demand trade-series query." />
            }
          >
            <BacktestTradeReviewPanel details={details} selectedSymbol={selectedSymbol} />
          </Suspense>
        );
      case 'analytics':
        return (
          <Suspense
            fallback={
              <BacktestPanelPendingState message="Loading the analytics chunk and its deferred equity or trade queries." />
            }
          >
            <BacktestAnalyticsPanel details={details} />
          </Suspense>
        );
      case 'overview':
        return (
          <BacktestOverviewPanel
            details={details}
            transportLabel={transportLabel}
            lastLiveEventAt={lastLiveEventAt}
            transportError={transportError}
          />
        );
      default:
        return (
          <EmptyState
            title="Unknown review section"
            description="Choose a valid backtest review section from the tabs above."
            tone="warning"
          />
        );
    }
  };

  return (
    <Stack spacing={2.5}>
      <RouteActionBar
        sticky
        title={`Run #${details.id} research workspace`}
        description="Overview now opens first so the route can land on cheap summary data, while workspace, trades, and analytics load independently on demand."
        actions={
          <>
            {onReplay ? (
              <Button variant="outlined" startIcon={<ReplayIcon />} onClick={onReplay}>
                Replay
              </Button>
            ) : null}
            {onFocusHistory ? (
              <Button variant="outlined" startIcon={<TableRowsIcon />} onClick={onFocusHistory}>
                Compare in history
              </Button>
            ) : null}
            <Button variant="outlined" startIcon={<ContentCopyIcon />} onClick={() => void copyShareableLink()}>
              Copy link
            </Button>
            <Button
              variant="contained"
              startIcon={<DownloadIcon />}
              onClick={() => void exportPdf()}
              disabled={!hasCompleteProvenance}
            >
              Export PDF
            </Button>
            {onDelete ? (
              <Button
                variant="outlined"
                color="error"
                startIcon={<DeleteOutlineIcon />}
                onClick={onDelete}
                disabled={deleteDisabled}
              >
                Delete
              </Button>
            ) : null}
          </>
        }
        meta={
          <>
            <StatusPill
              label={`Validation: ${details.validationStatus}`}
              tone={validationTone(details.validationStatus)}
              variant="filled"
            />
            <StatusPill label={`Status: ${details.executionStatus}`} tone="info" />
            <StatusPill label={`Market: ${formatBacktestMarketLabel(details.symbol)} (${details.timeframe})`} />
            <StatusPill label={`Transport: ${transportLabel}`} tone={transportError ? 'warning' : 'success'} />
            <StatusPill
              label={`Last pushed event: ${formatLiveEventLabel(lastLiveEventAt)}`}
              tone={transportError ? 'warning' : 'default'}
            />
          </>
        }
      />

      {feedback ? (
        <Alert
          severity={feedback.toLowerCase().includes('unable') ? 'warning' : 'success'}
          onClose={() => setFeedback(null)}
        >
          {feedback}
        </Alert>
      ) : null}

      <Tabs
        value={activeSection}
        onChange={(_, value: BacktestResultSection) => {
          const nextParams = new URLSearchParams(searchParams);
          nextParams.set('section', value);
          setSearchParams(nextParams, { replace: true });
        }}
        variant="scrollable"
        allowScrollButtonsMobile
        aria-label="Backtest result sections"
      >
        <Tab value="overview" label="Overview" />
        <Tab value="workspace" label="Workspace" />
        <Tab value="trades" label="Trades" />
        <Tab value="analytics" label="Analytics" />
      </Tabs>

      <Box ref={exportRef}>{renderActivePanel()}</Box>
    </Stack>
  );
}
