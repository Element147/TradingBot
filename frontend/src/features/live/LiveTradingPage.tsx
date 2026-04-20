import { Alert, Chip, Grid, Stack, Typography } from '@mui/material';
import { useMemo, useState } from 'react';

import { AppLayout } from '@/components/layout/AppLayout';
import { PageContent, PageIntro } from '@/components/layout/PageContent';
import { EmptyState, StatusPill, SurfacePanel } from '@/components/ui/Workbench';
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
import {
  useGetBalanceQuery,
  useGetOpenPositionsQuery,
  useGetPerformanceQuery,
  useGetRecentTradesQuery,
} from '@/features/account/accountApi';
import { executionContextMeta } from '@/features/execution/executionContext';
import { ForwardSignalTimelineChart } from '@/features/forwardTesting/ForwardSignalTimelineChart';
import {
  useGetAuditEventsQuery,
  useGetExchangeConnectionStatusQuery,
  useGetSavedExchangeConnectionsQuery,
} from '@/features/settings/exchangeApi';
import {
  useGetStrategiesQuery,
  useGetStrategyConfigHistoryQuery,
  type Strategy,
} from '@/features/strategies/strategiesApi';
import { getStrategyProfile } from '@/features/strategies/strategyProfiles';
import { useGetTradeHistoryQuery } from '@/features/trades/tradesApi';
import { getApiErrorMessage } from '@/services/api';

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

const resolveSelectedStrategy = (
  selectedStrategyId: number | null,
  strategies: Strategy[]
) =>
  selectedStrategyId && strategies.some((strategy) => strategy.id === selectedStrategyId)
    ? strategies.find((strategy) => strategy.id === selectedStrategyId) ?? null
    : strategies[0] ?? null;

export default function LiveTradingPage() {
  const routeExecutionContext = executionContextMeta.live;
  const [selectedStrategyId, setSelectedStrategyId] = useState<number | null>(null);
  const { data: savedConnections } = useGetSavedExchangeConnectionsQuery();
  const { data: connectionStatus, isError: isConnectionStatusError } =
    useGetExchangeConnectionStatusQuery(undefined, {
      pollingInterval: 15000,
      skipPollingIfUnfocused: true,
    });
  const { data: strategies = [], isLoading: isStrategiesLoading, isError: isStrategiesError } =
    useGetStrategiesQuery(
      { executionContext: 'live' },
      {
        pollingInterval: 20000,
        skipPollingIfUnfocused: true,
      }
    );

  const selectedStrategy = useMemo(
    () => resolveSelectedStrategy(selectedStrategyId, strategies),
    [selectedStrategyId, strategies]
  );

  const { data: configHistory = [], isLoading: isHistoryLoading } =
    useGetStrategyConfigHistoryQuery(
      selectedStrategy
        ? { strategyId: selectedStrategy.id, executionContext: 'live' }
        : undefined,
      {
        skip: !selectedStrategy,
      }
    );
  const { data: strategyTradeHistory, isLoading: isStrategyTradeHistoryLoading } =
    useGetTradeHistoryQuery(
      selectedStrategy
        ? {
            symbol: selectedStrategy.symbol,
            limit: 12,
            executionContext: 'live',
          }
        : undefined,
      {
        skip: !selectedStrategy,
        pollingInterval: 20000,
        skipPollingIfUnfocused: true,
      }
    );
  const {
    data: balance,
    error: balanceError,
    isError: isBalanceError,
  } = useGetBalanceQuery(
    { executionContext: 'live' },
    {
      pollingInterval: 30000,
      skipPollingIfUnfocused: true,
    }
  );
  const {
    data: performance,
    error: performanceError,
    isError: isPerformanceError,
  } = useGetPerformanceQuery(
    { timeframe: 'month', executionContext: 'live' },
    {
      pollingInterval: 30000,
      skipPollingIfUnfocused: true,
    }
  );
  const {
    data: positions = [],
    error: positionsError,
    isError: isPositionsError,
  } = useGetOpenPositionsQuery(
    { executionContext: 'live' },
    {
      pollingInterval: 30000,
      skipPollingIfUnfocused: true,
    }
  );
  const {
    data: recentTrades = [],
    error: recentTradesError,
    isError: isRecentTradesError,
  } = useGetRecentTradesQuery(
    { limit: 8, executionContext: 'live' },
    {
      pollingInterval: 30000,
      skipPollingIfUnfocused: true,
    }
  );
  const { data: auditEvents, isLoading: isAuditLoading } = useGetAuditEventsQuery({
    environment: 'live',
    targetType: 'STRATEGY',
    limit: 8,
  });

  const selectedTrades = useMemo(
    () => strategyTradeHistory?.items ?? [],
    [strategyTradeHistory?.items]
  );

  const liveReadCapabilityReason =
    (isBalanceError && getApiErrorMessage(balanceError, '')) ||
    (isPerformanceError && getApiErrorMessage(performanceError, '')) ||
    (isPositionsError && getApiErrorMessage(positionsError, '')) ||
    (isRecentTradesError && getApiErrorMessage(recentTradesError, '')) ||
    'The backend has not reported approved live account-read capability yet.';

  const supportsLiveAccountReads =
    !isBalanceError && !isPerformanceError && !isPositionsError && !isRecentTradesError;
  const supportsApprovedLiveExecution = false;
  const activeConnection =
    savedConnections?.connections.find(
      (connection) => connection.id === savedConnections.activeConnectionId
    ) ?? null;

  const statusItems = useMemo<ExecutionStatusItem[]>(
    () => [
      {
        label: 'Execution context',
        value: routeExecutionContext.label,
        detail: routeExecutionContext.description,
        tone: 'warning',
      },
      {
        label: 'Exchange connectivity',
        value: connectionStatus?.connected ? 'Connected' : 'Not connected',
        detail: connectionStatus?.error ?? connectionStatus?.rateLimitUsage ?? 'No exchange heartbeat yet.',
        tone: connectionStatus?.connected ? 'success' : 'warning',
      },
      {
        label: 'Live account reads',
        value: supportsLiveAccountReads ? 'Supported' : 'Unavailable',
        detail: supportsLiveAccountReads
          ? `Last sync ${formatTimestamp(balance?.lastSync ?? connectionStatus?.lastSync)}`
          : liveReadCapabilityReason,
        tone: supportsLiveAccountReads ? 'success' : 'warning',
      },
      {
        label: 'Live execution',
        value: supportsApprovedLiveExecution ? 'Approved' : 'Fail-closed',
        detail: supportsApprovedLiveExecution
          ? 'Exchange-scoped strategy actions are enabled.'
          : 'Strategy assignment, parameter edits, and order actions remain hidden until an explicit backend capability is added.',
        tone: supportsApprovedLiveExecution ? 'error' : 'info',
      },
    ],
    [
      balance?.lastSync,
      connectionStatus?.connected,
      connectionStatus?.error,
      connectionStatus?.lastSync,
      connectionStatus?.rateLimitUsage,
      liveReadCapabilityReason,
      routeExecutionContext.description,
      routeExecutionContext.label,
      supportsApprovedLiveExecution,
      supportsLiveAccountReads,
    ]
  );

  const metricItems = useMemo<LiveMetricItem[]>(() => {
    const selectedOpenTradeCount = selectedTrades.filter((trade) => !trade.exitTime).length;

    return [
      {
        label: 'Account total',
        value: balance ? formatNumber(balance.total) : 'Unavailable',
        detail: balance
          ? `Available ${formatNumber(balance.available)} | Locked ${formatNumber(balance.locked)}`
          : liveReadCapabilityReason,
        tone: balance ? 'info' : 'warning',
        kicker: 'Live',
      },
      {
        label: 'Month PnL',
        value: performance ? formatNumber(performance.totalProfitLoss) : formatNumber(selectedStrategy?.profitLoss),
        detail: performance
          ? formatPercent(performance.profitLossPercentage)
          : selectedStrategy
            ? `${selectedStrategy.tradeCount} strategy trades recorded`
            : liveReadCapabilityReason,
        tone:
          performance
            ? Number(performance.totalProfitLoss) >= 0
              ? 'success'
              : 'warning'
            : selectedStrategy && selectedStrategy.profitLoss >= 0
              ? 'success'
              : 'warning',
        kicker: 'Performance',
      },
      {
        label: 'Open exposure',
        value: String(supportsLiveAccountReads ? positions.length : selectedOpenTradeCount),
        detail: supportsLiveAccountReads
          ? `${recentTrades.length} recent live fills`
          : selectedStrategy
            ? `${selectedTrades.length} monitored strategy trades`
            : liveReadCapabilityReason,
        tone:
          (supportsLiveAccountReads ? positions.length : selectedOpenTradeCount) > 0
            ? 'warning'
            : 'success',
        kicker: 'Exposure',
      },
      {
        label: 'Connection profile',
        value: activeConnection
          ? `${activeConnection.name}`
          : 'No saved live profile',
        detail: activeConnection
          ? `${activeConnection.exchange.toUpperCase()} ${activeConnection.testnet ? 'TESTNET' : 'LIVE PROFILE'}`
          : 'Create and activate a saved exchange profile before expecting live monitoring.',
        tone: activeConnection ? (activeConnection.testnet ? 'warning' : 'info') : 'warning',
        kicker: 'Routing',
      },
    ];
  }, [
    activeConnection,
    balance,
    liveReadCapabilityReason,
    performance,
    positions.length,
    recentTrades.length,
    selectedStrategy,
    selectedTrades,
    supportsLiveAccountReads,
  ]);

  const detailSections = useMemo<ActiveAlgorithmDetailSection[]>(() => {
    if (!selectedStrategy) {
      return [];
    }

    const profile = getStrategyProfile(selectedStrategy.type);

    return [
      {
        id: 'capability-gate',
        title: 'Capability gate',
        content: (
          <Stack spacing={1}>
            <Typography variant="body2" color="text.secondary">
              Live execution stays blocked until the backend exposes an explicit capability that approves exchange-scoped assignment, parameter changes, and order placement from this route.
            </Typography>
            <Typography variant="body2">
              <strong>Current posture:</strong> monitor-only live review.
            </Typography>
            <Typography variant="body2">
              <strong>Backend reason:</strong> {liveReadCapabilityReason}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'signal-context',
        title: 'Signal and risk context',
        content: (
          <Stack spacing={0.75}>
            <Typography variant="body2">
              <strong>Strategy type:</strong> {profile?.title ?? selectedStrategy.type}
            </Typography>
            <Typography variant="body2">
              <strong>Entry rule:</strong> {profile?.entryRule ?? 'No strategy profile metadata is available.'}
            </Typography>
            <Typography variant="body2">
              <strong>Exit rule:</strong> {profile?.exitRule ?? 'No strategy profile metadata is available.'}
            </Typography>
            <Typography variant="body2">
              <strong>Risk notes:</strong> {profile?.riskNotes ?? 'No strategy profile metadata is available.'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'config-lineage',
        title: 'Observed config lineage',
        content: configHistory.length > 0 ? (
          <Stack spacing={1}>
            {configHistory.slice(0, 4).map((entry) => (
              <Stack key={entry.id} spacing={0.35}>
                <Typography variant="subtitle2">{`v${entry.versionNumber} | ${entry.symbol} | ${entry.timeframe}`}</Typography>
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
            No live-context config history is available for this strategy yet.
          </Typography>
        ),
      },
    ];
  }, [configHistory, liveReadCapabilityReason, selectedStrategy]);

  const investigationEntries = useMemo<InvestigationLogEntry[]>(() => {
    const strategyTargetId = selectedStrategy ? String(selectedStrategy.id) : null;
    const auditLogEntries =
      auditEvents?.events
        .filter(
          (event) =>
            !strategyTargetId || event.targetId === null || event.targetId === strategyTargetId
        )
        .map((event) => ({
          id: `audit-${event.id}`,
          timestamp: formatTimestamp(event.createdAt),
          title: `${event.action} ${event.outcome.toLowerCase()}`,
          detail: event.details ?? `Actor ${event.actor} updated live strategy state.`,
          tone: event.outcome === 'FAILED' ? 'warning' as const : 'success' as const,
          tags: [event.environment, event.actor, event.targetType],
        })) ?? [];

    const capabilityEntry: InvestigationLogEntry = {
      id: 'capability-state',
      timestamp: 'Current route posture',
      title: supportsApprovedLiveExecution ? 'Live execution capability available' : 'Live execution capability unavailable',
      detail: supportsApprovedLiveExecution
        ? 'This route may expose live controls because the backend reported explicit approval.'
        : liveReadCapabilityReason,
      tone: supportsApprovedLiveExecution ? 'error' : 'warning',
      tags: ['Live route', supportsApprovedLiveExecution ? 'execution-enabled' : 'monitor-only'],
    };

    return [capabilityEntry, ...auditLogEntries];
  }, [auditEvents?.events, liveReadCapabilityReason, selectedStrategy, supportsApprovedLiveExecution]);

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Live monitoring workspace"
          description="Review explicit live-context posture, monitored strategy evidence, and exchange health without silently enabling orders."
          chips={
            <>
              <Chip label="Monitor only" variant="outlined" />
              <Chip label="Capability-gated" variant="outlined" />
              <Chip label="Fail-closed routing" variant="outlined" />
            </>
          }
        />

        <Alert severity={supportsApprovedLiveExecution ? 'warning' : 'info'}>
          This route owns the `{routeExecutionContext.label.toLowerCase()}` execution context.
          It stays fail-closed until the backend reports approved live execution capability, so
          exchange-scoped strategy assignment, custom parameter edits, and order entry are not
          available from this page.
        </Alert>

        {!supportsLiveAccountReads ? (
          <Alert severity="warning">
            {liveReadCapabilityReason}
          </Alert>
        ) : null}

        {isConnectionStatusError ? (
          <Alert severity="warning">
            Unable to confirm live exchange connectivity status right now.
          </Alert>
        ) : null}

        {isStrategiesError ? (
          <Alert severity="error">
            Unable to load live-context strategy monitoring candidates.
          </Alert>
        ) : null}

        <ExecutionStatusRail
          title="Live route posture"
          description="Keep connection state, account-read availability, and live execution approval visible before reviewing any active strategy evidence."
          items={statusItems}
        />

        <LiveMetricStrip
          title="Live monitoring summary"
          description="When live account reads are unavailable, strategy evidence stays visible while trade and order actions remain locked."
          items={metricItems}
        />

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, xl: 7 }}>
            <SurfacePanel
              title="Monitored live strategies"
              description="This list stays read-only until an explicit backend capability approves exchange-scoped selection and parameter controls."
            >
              {isStrategiesLoading ? (
                <Alert severity="info">Loading live monitoring strategies...</Alert>
              ) : strategies.length === 0 ? (
                <EmptyState
                  title="No monitored live strategies"
                  description="Live-context strategy evidence will appear here once the backend exposes live-monitored strategy state."
                  tone="info"
                />
              ) : (
                <Stack spacing={1.25}>
                  {strategies.map((strategy) => (
                    <ExecutionCard
                      key={strategy.id}
                      title={strategy.name}
                      subtitle={`${strategy.symbol} | ${strategy.timeframe} | ${strategy.type}`}
                      selected={strategy.id === selectedStrategy?.id}
                      onSelect={() => setSelectedStrategyId(strategy.id)}
                      ariaLabel={`Select ${strategy.name} in live monitoring`}
                      badges={
                        <>
                          <StatusPill
                            label={strategy.status}
                            tone={strategy.status === 'RUNNING' ? 'success' : 'default'}
                          />
                          <StatusPill
                            label={supportsApprovedLiveExecution ? 'Execution-approved' : 'Monitor only'}
                            tone={supportsApprovedLiveExecution ? 'error' : 'warning'}
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
                        {
                          label: 'Trades',
                          value: String(strategy.tradeCount),
                          tone: 'info',
                        },
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
                      detail={
                        supportsApprovedLiveExecution
                          ? 'Approved live routes may expose scoped parameter controls here once the backend capability exists.'
                          : 'Monitoring stays read-only: review the chart and detail drawer for signal evidence without exposing live order actions.'
                      }
                    />
                  ))}
                </Stack>
              )}
            </SurfacePanel>

            <ForwardSignalTimelineChart
              strategyName={selectedStrategy?.name ?? 'Selected live strategy'}
              trades={selectedTrades}
            />
          </Grid>

          <Grid size={{ xs: 12, xl: 5 }}>
            <ActiveAlgorithmExplainabilityPanel
              title={selectedStrategy ? `${selectedStrategy.name} live detail` : 'Live algorithm detail'}
              description="The live drawer keeps capability state, config lineage, and signal context together while the route remains read-only."
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
              profile={selectedStrategy ? getStrategyProfile(selectedStrategy.type) : null}
              trades={selectedTrades}
              incidents={investigationEntries}
              loading={isHistoryLoading || isStrategyTradeHistoryLoading}
              statusChips={
                selectedStrategy ? (
                  <>
                    <StatusPill
                      label={selectedStrategy.status}
                      tone={selectedStrategy.status === 'RUNNING' ? 'success' : 'default'}
                      variant="filled"
                    />
                    <StatusPill label={selectedStrategy.symbol} tone="info" />
                    <StatusPill label={selectedStrategy.timeframe} tone="info" />
                    <StatusPill
                      label={supportsApprovedLiveExecution ? 'Execution-approved' : 'Monitor only'}
                      tone={supportsApprovedLiveExecution ? 'error' : 'warning'}
                    />
                  </>
                ) : null
              }
              summary={
                selectedStrategy ? (
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      {getStrategyProfile(selectedStrategy.type)?.bestFor ??
                        'Select a monitored live strategy to inspect rule context and recent signal evidence.'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Current open exposure:</strong>{' '}
                      {selectedTrades.filter((trade) => !trade.exitTime).length}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Last config change:</strong>{' '}
                      {formatTimestamp(selectedStrategy.lastConfigChangedAt)}
                    </Typography>
                  </Stack>
                ) : null
              }
              extraSections={detailSections}
            />

            <InvestigationLogPanel
              title="Live incidents and audit trail"
              description="Capability-state warnings and live audit events stay in one review stream."
              entries={investigationEntries}
              loading={isAuditLoading}
            />
          </Grid>
        </Grid>
      </PageContent>
    </AppLayout>
  );
}
