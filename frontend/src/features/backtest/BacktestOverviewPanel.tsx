import { Alert, Grid, Stack, Typography } from '@mui/material';

import type { BacktestDetails } from './backtestApi';

import {
  MetricCard,
  SurfacePanel,
} from '@/components/ui/Workbench';
import { getStrategyProfile } from '@/features/strategies/strategyProfiles';
import { formatDateTime, formatDistanceToNow, formatNumber } from '@/utils/formatters';

interface BacktestOverviewPanelProps {
  details: BacktestDetails;
  transportLabel: string;
  lastLiveEventAt: string | null;
  transportError: string | null;
}

const formatLiveEventLabel = (value: string | null | undefined) =>
  value ? formatDistanceToNow(new Date(value)) : 'No live progress event received yet';

export default function BacktestOverviewPanel({
  details,
  transportLabel,
  lastLiveEventAt,
  transportError,
}: BacktestOverviewPanelProps) {
  const profile = getStrategyProfile(details.strategyId);
  const hasCompleteProvenance = Boolean(
    details.datasetId &&
      details.datasetChecksumSha256 &&
      details.datasetSchemaVersion &&
      details.datasetUploadedAt
  );
  const lastUpdateLabel = details.lastProgressAt
    ? formatDistanceToNow(new Date(details.lastProgressAt))
    : 'No progress updates yet';

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, sm: 6, xl: 3 }}>
        <MetricCard
          label="Sharpe ratio"
          value={details.sharpeRatio.toFixed(2)}
          detail="Risk-adjusted return under the current cost model."
          tone="info"
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, xl: 3 }}>
        <MetricCard
          label="Profit factor"
          value={details.profitFactor.toFixed(2)}
          detail="Gross profits divided by gross losses."
          tone={details.profitFactor >= 1 ? 'success' : 'warning'}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, xl: 3 }}>
        <MetricCard
          label="Win rate"
          value={`${details.winRate.toFixed(2)}%`}
          detail={`${details.totalTrades} recorded trades`}
          tone={details.winRate >= 50 ? 'success' : 'warning'}
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 6, xl: 3 }}>
        <MetricCard
          label="Final balance"
          value={details.finalBalance.toFixed(2)}
          detail={`Initial balance ${details.initialBalance.toFixed(2)}`}
          tone={details.finalBalance >= details.initialBalance ? 'success' : 'error'}
        />
      </Grid>

      <Grid size={{ xs: 12, lg: 6 }}>
        <SurfacePanel
          title="Execution telemetry"
          description="Open the heavier workspace tabs only when you need chart, trade, or analytics detail."
        >
          <Stack spacing={0.75}>
            <Typography variant="body2" color="text.secondary">
              Status: {details.executionStatus} | Stage: {details.executionStage} | Progress:{' '}
              {details.progressPercent}%
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Current data date:{' '}
              {details.currentDataTimestamp
                ? formatDateTime(details.currentDataTimestamp)
                : 'Waiting for first candle'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Candles processed: {formatNumber(details.processedCandles)} /{' '}
              {formatNumber(details.totalCandles)} | Remaining{' '}
              {Math.max(0, 100 - details.progressPercent)}%
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Started: {details.startedAt ? formatDateTime(details.startedAt) : 'Queued only'} |
              Last update: {lastUpdateLabel}
              {details.completedAt ? ` | Completed: ${formatDateTime(details.completedAt)}` : ''}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Transport: {transportLabel} | Last pushed event: {formatLiveEventLabel(lastLiveEventAt)}
              {transportError ? ` | Stream status: ${transportError}` : ''}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {details.statusMessage ?? 'No backend status message was recorded for this run.'}
            </Typography>
            {details.asyncMonitor?.timedOut ? (
              <Alert severity="error">
                The async monitor flagged this run as stale because it exceeded the expected backend
                update window.
              </Alert>
            ) : null}
            {details.errorMessage ? <Alert severity="error">{details.errorMessage}</Alert> : null}
          </Stack>
        </SurfacePanel>
      </Grid>

      <Grid size={{ xs: 12, lg: 6 }}>
        <SurfacePanel
          title="Reproducibility"
          description="Dataset identity and schema stay visible before you load the heavier review panes."
        >
          {hasCompleteProvenance ? (
            <Stack spacing={0.75}>
              <Typography variant="body2" color="text.secondary">
                Dataset #{details.datasetId} | {details.datasetName ?? '-'} | Schema{' '}
                {details.datasetSchemaVersion}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Experiment key: {details.experimentKey}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Checksum: {details.datasetChecksumSha256}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Uploaded: {formatDateTime(details.datasetUploadedAt ?? '')}
                {details.datasetArchived ? ' | Archived from active catalog' : ' | Active in dataset catalog'}
              </Typography>
            </Stack>
          ) : (
            <Alert severity="warning">
              This run is missing full dataset provenance. Charts remain available, but report export is
              intentionally blocked.
            </Alert>
          )}
        </SurfacePanel>
      </Grid>

      {profile ? (
        <Grid size={{ xs: 12 }}>
          <SurfacePanel
            title="Strategy explainability"
            description="The same rule labels and evidence checklist are reused by Backtest, Forward Testing, Paper, and Live review surfaces."
          >
            <Stack spacing={0.75}>
              <Typography variant="body2" color="text.secondary">
                {profile.bestFor}
              </Typography>
              <Typography variant="body2">
                <strong>Entry rule:</strong> {profile.entryRule}
              </Typography>
              <Typography variant="body2">
                <strong>Exit rule:</strong> {profile.exitRule}
              </Typography>
              <Typography variant="body2">
                <strong>Stand aside:</strong> {profile.standAsideRule ?? 'No explicit stand-aside rule recorded.'}
              </Typography>
              <Typography variant="body2">
                <strong>Timeframe guidance:</strong> {profile.timeframeGuidance ?? profile.timeframeOptions.join(', ')}
              </Typography>
              <Typography variant="body2">
                <strong>Entry labels:</strong> {profile.entryReasons?.join(', ') ?? 'Unavailable'}
              </Typography>
              <Typography variant="body2">
                <strong>Exit labels:</strong> {profile.exitReasons?.join(', ') ?? 'Unavailable'}
              </Typography>
              <Typography variant="body2">
                <strong>Stand-aside labels:</strong> {profile.standAsideReasons?.join(', ') ?? 'Unavailable'}
              </Typography>
              <Typography variant="body2">
                <strong>Evidence checklist:</strong> {profile.indicatorChecklist?.join(', ') ?? 'Unavailable'}
              </Typography>
              <Typography variant="body2">
                <strong>Operator notes:</strong> {profile.operatorNotes?.join(', ') ?? profile.operatorAction}
              </Typography>
            </Stack>
          </SurfacePanel>
        </Grid>
      ) : null}

      {details.strategyMetrics && details.strategyMetrics.length > 0 ? (
        <Grid size={{ xs: 12 }}>
          <SurfacePanel
            title="Strategy-specific metrics"
            description="Supplemental run metrics that only apply to the active strategy hypothesis."
          >
            <Grid container spacing={2}>
              {details.strategyMetrics.map((metric) => (
                <Grid key={metric.key} size={{ xs: 12, sm: 6, xl: 3 }}>
                  <MetricCard
                    label={metric.label}
                    value={metric.displayValue}
                    detail={metric.description}
                    tone="info"
                  />
                </Grid>
              ))}
            </Grid>
          </SurfacePanel>
        </Grid>
      ) : null}
    </Grid>
  );
}
