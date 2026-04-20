import { Stack, Typography, type Breakpoint } from '@mui/material';
import type { ReactNode } from 'react';
import { useMemo } from 'react';

import {
  ActiveAlgorithmDetailDrawer,
  type ActiveAlgorithmDetailSection,
  type InvestigationLogEntry,
} from './ExecutionWorkspacePrimitives';

import { NumericText, StatusPill } from '@/components/ui/Workbench';

type ExplainabilityTone = 'success' | 'warning' | 'info' | 'default' | 'error';

export interface ActiveAlgorithmExplainabilityTrade {
  id: number | string;
  pair?: string;
  entryTime: string;
  entryPrice: number | string;
  exitTime: string | null;
  exitPrice: number | string | null;
  signal: string;
  positionSide: string;
  positionSize: number | string;
  riskAmount: number | string;
  pnl: number | string;
  stopLoss: number | string | null;
  takeProfit: number | string | null;
}

export interface ActiveAlgorithmExplainabilitySubject {
  name: string;
  status: string;
  symbol: string;
  timeframe: string;
  riskPerTrade: number;
  minPositionSize: number;
  maxPositionSize: number;
  configVersion: number;
  lastConfigChangedAt: string | null;
}

export interface ActiveAlgorithmExplainabilityProfile {
  title?: string;
  bestFor?: string;
  shortDescription?: string;
  entryRule?: string;
  exitRule?: string;
  standAsideRule?: string;
  riskNotes?: string;
  timeframeGuidance?: string;
  entryReasons?: string[];
  exitReasons?: string[];
  standAsideReasons?: string[];
  indicatorChecklist?: string[];
  operatorNotes?: string[];
}

interface ActiveAlgorithmExplainabilityPanelProps {
  title: ReactNode;
  description?: ReactNode;
  subject: ActiveAlgorithmExplainabilitySubject | null;
  profile?: ActiveAlgorithmExplainabilityProfile | null;
  trades: ActiveAlgorithmExplainabilityTrade[];
  incidents?: InvestigationLogEntry[];
  loading?: boolean;
  extraSections?: ActiveAlgorithmDetailSection[];
  summary?: ReactNode;
  statusChips?: ReactNode;
  desktopBehavior?: 'sticky' | 'inline';
  desktopBreakpoint?: Breakpoint;
}

const numberFormatter = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const percentFormatter = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const timestampFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const formatNumber = (value: string | number | null | undefined) => {
  if (value === null || value === undefined) {
    return 'Unavailable';
  }

  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? numberFormatter.format(numeric) : String(value);
};

const formatPercent = (value: number | null | undefined) =>
  value === null || value === undefined
    ? 'Unavailable'
    : `${percentFormatter.format(value * 100)}%`;

const formatTimestamp = (value: string | null | undefined) =>
  value ? timestampFormatter.format(new Date(value)) : 'No recent update';

const renderReasonList = (label: string, values?: string[]) =>
  values && values.length > 0 ? (
    <Typography variant="body2">
      <strong>{label}:</strong> {values.join(', ')}
    </Typography>
  ) : null;

const resolveIncidentTone = (incident?: InvestigationLogEntry): ExplainabilityTone => {
  if (!incident?.tone) {
    return 'default';
  }

  return incident.tone;
};

export function ActiveAlgorithmExplainabilityPanel({
  title,
  description,
  subject,
  profile,
  trades,
  incidents = [],
  loading = false,
  extraSections = [],
  summary,
  statusChips,
  desktopBehavior = 'sticky',
  desktopBreakpoint = 'xl',
}: ActiveAlgorithmExplainabilityPanelProps) {
  const sections = useMemo<ActiveAlgorithmDetailSection[]>(() => {
    if (!subject) {
      return extraSections;
    }

    const openTrades = trades.filter((trade) => !trade.exitTime);
    const latestTrade = trades[0] ?? null;

    const sharedSections: ActiveAlgorithmDetailSection[] = [
      {
        id: 'decision-evidence',
        title: 'Entry and exit evidence',
        content: trades.length > 0 ? (
          <Stack spacing={1}>
            {trades.slice(0, 4).map((trade) => (
              <Stack key={trade.id} spacing={0.4}>
                <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                  {`${trade.pair ?? subject.symbol} | ${trade.positionSide} | ${trade.signal}`}
                </Typography>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  Entry {formatNumber(trade.entryPrice)} at {formatTimestamp(trade.entryTime)} | Exit{' '}
                  {trade.exitTime ? `${formatNumber(trade.exitPrice)} at ${formatTimestamp(trade.exitTime)}` : 'Open'}
                </Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Entry and exit markers will appear here once this algorithm records monitored trade activity.
          </Typography>
        ),
      },
      {
        id: 'signal-reason',
        title: 'Signal and decision reason',
        content: (
          <Stack spacing={0.75}>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {profile?.shortDescription ?? 'Shared explainability metadata is not available for this strategy yet.'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Entry rule:</strong> {profile?.entryRule ?? latestTrade?.signal ?? 'Unavailable'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Exit rule:</strong> {profile?.exitRule ?? 'Unavailable'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Stand aside:</strong> {profile?.standAsideRule ?? 'No stand-aside rule recorded'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Latest trigger:</strong> {latestTrade ? latestTrade.signal : 'No recent trade trigger recorded'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'reason-labels-evidence',
        title: 'Reason labels and evidence',
        content: (
          <Stack spacing={0.75}>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              Use these strategy-specific labels when reviewing why the algorithm entered, exited, or stood aside across Backtest, Forward Testing, Paper, and Live.
            </Typography>
            {renderReasonList('Entry labels', profile?.entryReasons)}
            {renderReasonList('Exit labels', profile?.exitReasons)}
            {renderReasonList('Stand-aside labels', profile?.standAsideReasons)}
            {renderReasonList('Evidence checklist', profile?.indicatorChecklist)}
            {renderReasonList('Operator notes', profile?.operatorNotes)}
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Timeframe guidance:</strong> {profile?.timeframeGuidance ?? 'Use the configured timeframe and review the matching chart overlays.'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'risk-pnl',
        title: 'Current risk and PnL stats',
        content: (
          <Stack spacing={0.75}>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Risk / trade:</strong> {formatPercent(subject.riskPerTrade)}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Position sizing:</strong> {formatNumber(subject.minPositionSize)} to {formatNumber(subject.maxPositionSize)}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Latest PnL:</strong> {latestTrade ? formatNumber(latestTrade.pnl) : 'Unavailable'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Latest trade risk:</strong> {latestTrade ? formatNumber(latestTrade.riskAmount) : 'Unavailable'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Stops:</strong> {latestTrade?.stopLoss ? formatNumber(latestTrade.stopLoss) : 'None'} | <strong>Targets:</strong>{' '}
              {latestTrade?.takeProfit ? formatNumber(latestTrade.takeProfit) : 'None'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'position-exposure',
        title: 'Position state and exposure',
        content: (
          <Stack spacing={0.75}>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Open positions:</strong> {openTrades.length}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Current exposure:</strong> {latestTrade ? `${latestTrade.positionSide} ${formatNumber(latestTrade.positionSize)}` : 'No active exposure recorded'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Config version:</strong> v{subject.configVersion}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Last config change:</strong> {formatTimestamp(subject.lastConfigChangedAt)}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'incidents-overrides',
        title: 'Recent incidents or overrides',
        content: incidents.length > 0 ? (
          <Stack spacing={1}>
            {incidents.slice(0, 4).map((incident) => (
              <Stack key={incident.id} spacing={0.4}>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap alignItems="center">
                  <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                    {incident.title}
                  </Typography>
                  <StatusPill label={incident.timestamp} tone={resolveIncidentTone(incident)} />
                </Stack>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  {incident.detail}
                </Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No recent incidents, overrides, or operator interventions are attached to this algorithm yet.
          </Typography>
        ),
      },
    ];

    return [...sharedSections, ...extraSections];
  }, [
    extraSections,
    incidents,
    profile?.entryReasons,
    profile?.entryRule,
    profile?.exitReasons,
    profile?.exitRule,
    profile?.indicatorChecklist,
    profile?.operatorNotes,
    profile?.shortDescription,
    profile?.standAsideReasons,
    profile?.standAsideRule,
    profile?.timeframeGuidance,
    subject,
    trades,
  ]);

  const defaultStatusChips =
    subject ? (
      <>
        <StatusPill
          label={subject.status}
          tone={subject.status === 'RUNNING' ? 'success' : 'default'}
          variant="filled"
        />
        <StatusPill label={subject.symbol} tone="info" />
        <StatusPill label={subject.timeframe} tone="info" />
      </>
    ) : null;

  const defaultSummary =
    subject ? (
      <Stack spacing={1}>
        <Typography variant="body2" color="text.secondary">
          {profile?.bestFor ?? 'Select an algorithm to inspect rule context, recent evidence, and current risk posture.'}
        </Typography>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <Stack spacing={0.35}>
            <Typography variant="caption" color="text.secondary">
              Risk / trade
            </Typography>
            <NumericText variant="body2">{formatPercent(subject.riskPerTrade)}</NumericText>
          </Stack>
          <Stack spacing={0.35}>
            <Typography variant="caption" color="text.secondary">
              Min size
            </Typography>
            <NumericText variant="body2">{formatNumber(subject.minPositionSize)}</NumericText>
          </Stack>
          <Stack spacing={0.35}>
            <Typography variant="caption" color="text.secondary">
              Max size
            </Typography>
            <NumericText variant="body2">{formatNumber(subject.maxPositionSize)}</NumericText>
          </Stack>
        </Stack>
      </Stack>
    ) : null;

  return (
    <ActiveAlgorithmDetailDrawer
      title={title}
      description={description}
      loading={loading}
      statusChips={statusChips ?? defaultStatusChips}
      summary={summary ?? defaultSummary}
      sections={sections}
      desktopBehavior={desktopBehavior}
      desktopBreakpoint={desktopBreakpoint}
    />
  );
}
