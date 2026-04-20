import {
  Alert,
  Button,
  Chip,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';

import { ForwardSignalTimelineChart } from './ForwardSignalTimelineChart';
import { useGetForwardTestingStatusQuery } from './forwardTestingApi';
import {
  appendForwardTestingNote,
  loadForwardTestingNotes,
  type ForwardTestingNote,
} from './forwardTestingNotes';

import { AppLayout } from '@/components/layout/AppLayout';
import { PageContent, PageIntro } from '@/components/layout/PageContent';
import { EmptyState, NumericText, StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import { ActiveAlgorithmExplainabilityPanel } from '@/components/workspace/ActiveAlgorithmExplainabilityPanel';
import {
  ExecutionCard,
  ExecutionStatusRail,
  InvestigationLogPanel,
  LiveMetricStrip,
  type ActiveAlgorithmDetailSection,
  type ExecutionStatusItem,
  type InvestigationLogEntry,
  type LiveMetricItem,
} from '@/components/workspace/ExecutionWorkspacePrimitives';
import { executionContextMeta } from '@/features/execution/executionContext';
import { useGetPaperTradingStateQuery } from '@/features/paperApi';
import { useGetAuditEventsQuery } from '@/features/settings/exchangeApi';
import {
  useGetStrategiesQuery,
  useGetStrategyConfigHistoryQuery,
  type Strategy,
} from '@/features/strategies/strategiesApi';
import { getStrategyProfile } from '@/features/strategies/strategyProfiles';
import {
  useGetTradeHistoryQuery,
  type TradeHistoryItem,
} from '@/features/trades/tradesApi';
import { sanitizeText } from '@/utils/security';

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

const formatPercent = (value: string | number | null | undefined) => {
  if (value === null || value === undefined) {
    return 'Unavailable';
  }

  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? `${percentFormatter.format(numeric)}%` : String(value);
};

const formatTimestamp = (value: string | null | undefined) =>
  value ? timestampFormatter.format(new Date(value)) : 'No recent update';

const summarizeSignalStack = (trades: TradeHistoryItem[]) => {
  const longCount = trades.filter((trade) => trade.positionSide === 'LONG').length;
  const shortCount = trades.filter((trade) => trade.positionSide === 'SHORT').length;
  const openCount = trades.filter((trade) => !trade.exitTime).length;

  return `${longCount} long, ${shortCount} short, ${openCount} still open`;
};

const profileSummary = (strategy: Strategy) => {
  const profile = getStrategyProfile(strategy.type);
  return (
    profile?.shortDescription ??
    `Review ${strategy.name} in forward testing to inspect its paper-safe signal behavior and recent monitored trade evidence.`
  );
};

export default function ForwardTestingPage() {
  const routeExecutionContext = executionContextMeta['forward-test'];
  const { data: strategies = [], isLoading: isStrategiesLoading, isError: isStrategiesError } =
    useGetStrategiesQuery(
      { executionContext: 'forward-test' },
      {
        pollingInterval: 15000,
        skipPollingIfUnfocused: true,
      }
    );
  const [selectedStrategyId, setSelectedStrategyId] = useState<number | null>(null);
  const [notes, setNotes] = useState<ForwardTestingNote[]>(() => loadForwardTestingNotes());
  const [noteDraft, setNoteDraft] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);
  const effectiveSelectedStrategyId =
    selectedStrategyId && strategies.some((strategy) => strategy.id === selectedStrategyId)
      ? selectedStrategyId
      : strategies[0]?.id ?? null;

  const selectedStrategy =
    strategies.find((strategy) => strategy.id === effectiveSelectedStrategyId) ?? null;
  const profile = selectedStrategy ? getStrategyProfile(selectedStrategy.type) : null;

  const { data: configHistory = [], isLoading: isHistoryLoading } =
    useGetStrategyConfigHistoryQuery(
      selectedStrategy
        ? { strategyId: selectedStrategy.id, executionContext: 'forward-test' }
        : undefined,
      {
        skip: !selectedStrategy,
      }
    );
  const { data: tradeHistory, isLoading: isTradeHistoryLoading } = useGetTradeHistoryQuery(
    selectedStrategy
      ? {
          symbol: selectedStrategy.symbol,
          limit: 12,
          executionContext: 'forward-test',
        }
      : undefined,
    {
      skip: !selectedStrategy,
      pollingInterval: 15000,
      skipPollingIfUnfocused: true,
    }
  );
  const { data: paperState } = useGetPaperTradingStateQuery(
    { executionContext: 'forward-test' },
    {
      pollingInterval: 20000,
      skipPollingIfUnfocused: true,
    }
  );
  const { data: auditEvents, isLoading: isAuditLoading } = useGetAuditEventsQuery({
    environment: 'paper',
    targetType: 'STRATEGY',
    limit: 8,
  });
  const { data: forwardStatus } = useGetForwardTestingStatusQuery(undefined, {
    pollingInterval: 15000,
    skipPollingIfUnfocused: true,
  });

  const selectedStrategyNotes = useMemo(
    () =>
      selectedStrategy
        ? notes.filter((note) => note.strategyId === selectedStrategy.id)
        : [],
    [notes, selectedStrategy]
  );
  const trades = useMemo(() => tradeHistory?.items ?? [], [tradeHistory?.items]);

  const statusItems = useMemo<ExecutionStatusItem[]>(() => {
    if (!selectedStrategy) {
      return [];
    }

    return [
      {
        label: 'Execution context',
        value: routeExecutionContext.label,
        detail: routeExecutionContext.description,
        tone: 'info' as const,
      },
      {
        label: 'Strategy version',
        value: `v${selectedStrategy.configVersion}`,
        detail: `Last changed ${formatTimestamp(selectedStrategy.lastConfigChangedAt)}`,
        tone: 'info' as const,
      },
      {
        label: 'Signal freshness',
        value: trades[0]?.entryTime ? formatTimestamp(trades[0].entryTime) : 'Awaiting signal flow',
        detail: trades.length > 0 ? summarizeSignalStack(trades) : 'No recent paper-safe trade evidence.',
        tone: trades.length > 0 ? 'success' as const : 'warning' as const,
      },
      {
        label: 'Paper recovery',
        value: paperState?.recoveryStatus ?? 'Loading',
        detail: paperState?.recoveryMessage ?? 'Checking recovery posture for this workspace.',
        tone:
          paperState?.recoveryStatus === 'ATTENTION'
            ? 'warning'
            : paperState?.recoveryStatus === 'HEALTHY'
              ? 'success'
              : 'info',
      },
    ];
  }, [paperState, routeExecutionContext.description, routeExecutionContext.label, selectedStrategy, trades]);

  const liveMetricItems = useMemo<LiveMetricItem[]>(() => {
    if (!forwardStatus) {
      return [];
    }

    return [
      {
        label: 'Observed PnL',
        value: formatNumber(forwardStatus.pnl),
        detail: formatPercent(forwardStatus.pnlPercent),
        tone: Number(forwardStatus.pnl) >= 0 ? 'success' as const : 'warning' as const,
        kicker: 'Forward',
      },
      {
        label: 'Sharpe',
        value: formatNumber(forwardStatus.sharpeRatio),
        detail: `Profit factor ${formatNumber(forwardStatus.profitFactor)}`,
        tone: 'info' as const,
        kicker: 'Risk-adjusted',
      },
      {
        label: 'Drawdown',
        value: formatNumber(forwardStatus.maxDrawdown),
        detail: formatPercent(forwardStatus.maxDrawdownPercent),
        tone: Number(forwardStatus.maxDrawdown) > 0 ? 'warning' as const : 'success' as const,
        kicker: 'Peak-to-trough',
      },
      {
        label: 'Open positions',
        value: String(forwardStatus.openPositions),
        detail: `${forwardStatus.totalTrades} tracked trades | ${formatPercent(forwardStatus.winRate)} win rate`,
        tone: 'info' as const,
        kicker: forwardStatus.status,
      },
    ];
  }, [forwardStatus]);

  const investigationEntries = useMemo<InvestigationLogEntry[]>(() => {
    const strategyTargetId = selectedStrategy ? String(selectedStrategy.id) : null;
    const strategyAuditEvents =
      auditEvents?.events.filter(
        (event) => !strategyTargetId || event.targetId === null || event.targetId === strategyTargetId
      ) ?? [];

    const noteEntries = selectedStrategyNotes.map((note) => ({
      id: `note-${note.id}`,
      timestamp: formatTimestamp(note.createdAt),
      title: 'Operator note',
      detail: note.body,
      tone: 'info' as const,
      tags: ['Local workstation note', note.strategyName],
    }));

    const auditLogEntries = strategyAuditEvents.map((event) => ({
      id: `audit-${event.id}`,
      timestamp: formatTimestamp(event.createdAt),
      title: `${event.action} ${event.outcome.toLowerCase()}`,
      detail: event.details ?? `Actor ${event.actor} updated ${event.targetType.toLowerCase()} state.`,
      tone: event.outcome === 'FAILED' ? 'warning' as const : 'success' as const,
      tags: [event.environment, event.targetType, event.actor],
    }));

    const alertEntries =
      paperState?.alerts.map((alert, index) => ({
        id: `alert-${index}`,
        timestamp: 'Current desk state',
        title: `${alert.code}: ${alert.summary}`,
        detail: alert.recommendedAction,
        tone: alert.severity === 'WARNING' ? 'warning' as const : 'info' as const,
        tags: ['Paper recovery', alert.severity],
      })) ?? [];

    return [...alertEntries, ...noteEntries, ...auditLogEntries];
  }, [auditEvents?.events, paperState?.alerts, selectedStrategy, selectedStrategyNotes]);

  const detailSections = useMemo<ActiveAlgorithmDetailSection[]>(() => {
    if (!selectedStrategy) {
      return [];
    }

    return [
      {
        id: 'signal-stack',
        title: 'Signal and indicator context',
        content: (
          <Stack spacing={1}>
            <Typography variant="body2" color="text.secondary">
              {profile?.shortDescription ??
                'No strategy profile metadata is available yet for this strategy type.'}
            </Typography>
            <Typography variant="body2">
              <strong>Entry:</strong> {profile?.entryRule ?? 'Profile metadata unavailable.'}
            </Typography>
            <Typography variant="body2">
              <strong>Exit:</strong> {profile?.exitRule ?? 'Profile metadata unavailable.'}
            </Typography>
            <Typography variant="body2">
              <strong>Risk notes:</strong> {profile?.riskNotes ?? 'Profile metadata unavailable.'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'config-lineage',
        title: 'Configuration lineage',
        content: configHistory.length > 0 ? (
          <Stack spacing={1}>
            {configHistory.slice(0, 4).map((entry) => (
              <Stack key={entry.id} spacing={0.35}>
                <Typography variant="subtitle2">{`v${entry.versionNumber} | ${entry.timeframe} | ${entry.symbol}`}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {entry.changeReason}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatTimestamp(entry.changedAt)}
                </Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No configuration history is available for this strategy yet.
          </Typography>
        ),
      },
      {
        id: 'position-state',
        title: 'Observed trade state',
        content: trades.length > 0 ? (
          <Stack spacing={1}>
            {trades.slice(0, 4).map((trade) => (
              <Stack key={trade.id} spacing={0.35}>
                <Typography variant="subtitle2">{`${trade.pair} | ${trade.positionSide} | ${trade.signal}`}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Entry {formatNumber(trade.entryPrice)} | Exit{' '}
                  {trade.exitPrice ? formatNumber(trade.exitPrice) : 'Open'} | PnL{' '}
                  {formatNumber(trade.pnl)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatTimestamp(trade.entryTime)}
                </Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Recent position evidence will appear here once the monitoring stream records trades for this strategy symbol.
          </Typography>
        ),
      },
    ];
  }, [configHistory, profile, selectedStrategy, trades]);

  const submitNote = () => {
    if (!selectedStrategy) {
      setFeedback('Select a strategy before adding an operator note.');
      return;
    }

    const trimmed = sanitizeText(noteDraft).trim();
    if (!trimmed) {
      setFeedback('Write a short operator note before saving.');
      return;
    }

    setNotes(
      appendForwardTestingNote({
        strategyId: selectedStrategy.id,
        strategyName: selectedStrategy.name,
        body: trimmed,
      })
    );
    setNoteDraft('');
    setFeedback('Operator note saved locally for this workstation.');
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Forward testing workspace"
          description="Observe strategy behavior, inspect recent signal evidence, and leave follow-up notes without opening a live execution path."
          chips={
            <>
              <Chip label="Observation only" variant="outlined" />
              <Chip label="Paper-safe routing" variant="outlined" />
              <Chip label="Audit context stays visible" variant="outlined" />
            </>
          }
        />

        {feedback ? (
          <Alert severity="info" onClose={() => setFeedback(null)}>
            {feedback}
          </Alert>
        ) : null}

        <Alert severity="info">
          This workspace owns the `{routeExecutionContext.label.toLowerCase()}` execution context,
          so it stays pinned to test-scoped monitoring, chart evidence, and operator review even if
          future live-only surfaces are added elsewhere.
        </Alert>

        {isStrategiesError ? (
          <Alert severity="error">
            Unable to load strategy monitoring candidates for forward testing.
          </Alert>
        ) : null}

        <ExecutionStatusRail
          title="Forward-testing posture"
          description="Keep strategy version, signal freshness, and paper recovery visible before drilling into chart evidence."
          items={statusItems}
        />

        <LiveMetricStrip
          title="Observed performance"
          description="These metrics summarize the monitored strategy account state without enabling orders from this route."
          items={liveMetricItems}
        />

        <Grid container spacing={2.5} alignItems="flex-start">
          <Grid size={{ xs: 12, xl: 7 }}>
            <SurfacePanel
              title="Strategy selection"
              description="Choose one strategy configuration to inspect recent signal behavior, context, and follow-up notes."
            >
              {isStrategiesLoading ? (
                <Alert severity="info">Loading forward-testing strategies...</Alert>
              ) : strategies.length === 0 ? (
                <EmptyState
                  title="No strategies available"
                  description="Create or restore a strategy configuration before using the forward-testing workspace."
                  tone="info"
                />
              ) : (
                <Stack spacing={1.25}>
                  {strategies.map((strategy) => (
                    <ExecutionCard
                      key={strategy.id}
                      title={strategy.name}
                      subtitle={`${strategy.symbol} | ${strategy.timeframe} | ${strategy.type}`}
                      selected={strategy.id === effectiveSelectedStrategyId}
                      onSelect={() => setSelectedStrategyId(strategy.id)}
                      ariaLabel={`Select ${strategy.name} for forward testing`}
                      badges={
                        <>
                          <StatusPill
                            label={strategy.status}
                            tone={strategy.status === 'RUNNING' ? 'success' : 'default'}
                          />
                          <StatusPill
                            label={strategy.paperMode ? 'Paper-backed' : 'Monitor only'}
                            tone={strategy.paperMode ? 'info' : 'warning'}
                          />
                          <StatusPill label={`Config v${strategy.configVersion}`} tone="info" />
                        </>
                      }
                      metrics={[
                        {
                          label: 'PnL',
                          value: formatNumber(strategy.profitLoss),
                          tone: strategy.profitLoss >= 0 ? 'success' : 'warning',
                        },
                        { label: 'Trades', value: String(strategy.tradeCount), tone: 'info' },
                        {
                          label: 'Drawdown',
                          value: formatNumber(strategy.currentDrawdown),
                          tone: strategy.currentDrawdown > 0 ? 'warning' : 'success',
                        },
                        {
                          label: 'Risk / trade',
                          value: formatPercent(strategy.riskPerTrade * 100),
                          tone: 'info',
                        },
                      ]}
                      detail={profileSummary(strategy)}
                    />
                  ))}
                </Stack>
              )}
            </SurfacePanel>

            <ForwardSignalTimelineChart
              strategyName={selectedStrategy?.name ?? 'Selected strategy'}
              trades={trades}
            />

            <SurfacePanel
              title="Operator notes"
              description="Notes are stored on this workstation only until a durable backend note flow is introduced."
            >
              <Stack spacing={1.5}>
                <TextField
                  label="Operator follow-up note"
                  minRows={3}
                  multiline
                  value={noteDraft}
                  onChange={(event) => setNoteDraft(sanitizeText(event.target.value))}
                  placeholder="Capture what you observed, what to verify next, and whether the signal should be replayed in backtest."
                />
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems="center">
                  <Button variant="contained" onClick={submitNote} disabled={!selectedStrategy}>
                    Save local note
                  </Button>
                  <Typography variant="body2" color="text.secondary">
                    Notes stay local to this browser profile and remain outside live execution paths.
                  </Typography>
                </Stack>
              </Stack>
            </SurfacePanel>
          </Grid>

          <Grid size={{ xs: 12, xl: 5 }}>
            <ActiveAlgorithmExplainabilityPanel
              title={selectedStrategy ? `${selectedStrategy.name} detail` : 'Forward-testing detail'}
              description="Move from strategy selection to signal explanation, config lineage, and recent observed state without leaving the workspace."
              desktopBehavior="inline"
              subject={
                selectedStrategy
                  ? {
                      name: selectedStrategy.name,
                      status: selectedStrategy.status,
                      symbol: selectedStrategy.symbol,
                      timeframe: selectedStrategy.timeframe,
                      riskPerTrade: selectedStrategy.riskPerTrade,
                      minPositionSize: selectedStrategy.minPositionSize,
                      maxPositionSize: selectedStrategy.maxPositionSize,
                      configVersion: selectedStrategy.configVersion,
                      lastConfigChangedAt: selectedStrategy.lastConfigChangedAt,
                    }
                  : null
              }
              profile={profile}
              trades={trades}
              incidents={investigationEntries}
              loading={isHistoryLoading || isTradeHistoryLoading}
              summary={
                selectedStrategy ? (
                  <Stack spacing={1.2}>
                    <Typography variant="body2" color="text.secondary">
                      {profile?.bestFor ??
                        'Select a strategy to inspect its intended use and current monitored state.'}
                    </Typography>
                    <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
                      <Stack spacing={0.35}>
                        <Typography variant="caption" color="text.secondary">
                          Min size
                        </Typography>
                        <NumericText variant="body2">
                          {formatNumber(selectedStrategy.minPositionSize)}
                        </NumericText>
                      </Stack>
                      <Stack spacing={0.35}>
                        <Typography variant="caption" color="text.secondary">
                          Max size
                        </Typography>
                        <NumericText variant="body2">
                          {formatNumber(selectedStrategy.maxPositionSize)}
                        </NumericText>
                      </Stack>
                      <Stack spacing={0.35}>
                        <Typography variant="caption" color="text.secondary">
                          Last config change
                        </Typography>
                        <Typography variant="body2">
                          {formatTimestamp(selectedStrategy.lastConfigChangedAt)}
                        </Typography>
                      </Stack>
                    </Stack>
                  </Stack>
                ) : null
              }
              extraSections={detailSections}
            />

            <InvestigationLogPanel
              title="Investigation history"
              description="Paper alerts, operator notes, and strategy audit events stay in one review trail."
              entries={investigationEntries}
              loading={isAuditLoading}
            />
          </Grid>
        </Grid>
      </PageContent>
    </AppLayout>
  );
}
