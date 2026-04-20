import {
  Alert,
  Button,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { PaperOrdersTablePanel } from './PaperOrdersTablePanel';
import {
  PaperOrderEntryPanel,
  PaperTradingSummaryPanel,
} from './PaperTradingPanels';
import {
  loadPaperWorkspaceAssignments,
  togglePaperWorkspaceAssignment,
  type PaperWorkspaceAssignment,
} from './paperWorkspaceAssignments';

import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
} from '@/components/layout/PageContent';
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
import { ForwardSignalTimelineChart } from '@/features/forwardTesting/ForwardSignalTimelineChart';
import {
  type PaperOrderSide,
  useCancelPaperOrderMutation,
  useFillPaperOrderMutation,
  useGetPaperOrdersQuery,
  useGetPaperTradingStateQuery,
  usePlacePaperOrderMutation,
} from '@/features/paperApi';
import { useGetAuditEventsQuery, useGetSavedExchangeConnectionsQuery } from '@/features/settings/exchangeApi';
import {
  useGetStrategiesQuery,
  useGetStrategyConfigHistoryQuery,
  useStartStrategyMutation,
  useStopStrategyMutation,
  useUpdateStrategyConfigMutation,
  type Strategy,
} from '@/features/strategies/strategiesApi';
import { StrategyConfigModal } from '@/features/strategies/StrategyConfigModal';
import { getStrategyProfile } from '@/features/strategies/strategyProfiles';
import type { StrategyConfigOutput } from '@/features/strategies/strategyValidation';
import { useGetTradeHistoryQuery } from '@/features/trades/tradesApi';
import { getApiErrorMessage } from '@/services/api';
import { sanitizeText } from '@/utils/security';

type PaperOrderFormState = {
  symbol: string;
  side: PaperOrderSide;
  quantity: string;
  price: string;
  executeNow: boolean;
};

const DEFAULT_FORM: PaperOrderFormState = {
  symbol: 'BTC/USDT',
  side: 'BUY',
  quantity: '0.05',
  price: '50000',
  executeNow: true,
};

export default function PaperTradingPage() {
  const { data: state, isLoading: isStateLoading, isError: isStateError } =
    useGetPaperTradingStateQuery({ executionContext: 'paper' }, {
      pollingInterval: 15000,
      skipPollingIfUnfocused: true,
    });
  const { data: orders = [], isLoading: isOrdersLoading, isError: isOrdersError } =
    useGetPaperOrdersQuery({ executionContext: 'paper' }, {
      pollingInterval: 10000,
      skipPollingIfUnfocused: true,
    });
  const { data: savedConnections } = useGetSavedExchangeConnectionsQuery();
  const { data: strategies = [] } = useGetStrategiesQuery(
    { executionContext: 'paper' },
    {
      pollingInterval: 30000,
      skipPollingIfUnfocused: true,
    }
  );
  const [startStrategy, { isLoading: isStartingStrategy }] = useStartStrategyMutation();
  const [stopStrategy, { isLoading: isStoppingStrategy }] = useStopStrategyMutation();
  const [updateConfig, { isLoading: isSavingConfig }] = useUpdateStrategyConfigMutation();
  const [placeOrder, { isLoading: isPlacing }] = usePlacePaperOrderMutation();
  const [fillOrder, { isLoading: isFilling }] = useFillPaperOrderMutation();
  const [cancelOrder, { isLoading: isCancelling }] = useCancelPaperOrderMutation();
  const [form, setForm] = useState<PaperOrderFormState>(DEFAULT_FORM);
  const [assignments, setAssignments] = useState<PaperWorkspaceAssignment[]>(() =>
    loadPaperWorkspaceAssignments()
  );
  const [selectedExchangeId, setSelectedExchangeId] = useState<string | null>(null);
  const [selectedAlgorithmId, setSelectedAlgorithmId] = useState<number | null>(null);
  const [editingStrategy, setEditingStrategy] = useState<Strategy | null>(null);
  const [feedback, setFeedback] = useState<{
    severity: 'success' | 'error';
    message: string;
  } | null>(null);

  const orderMutationBusy =
    isPlacing || isFilling || isCancelling || isStartingStrategy || isStoppingStrategy;
  const exchangeConnections = savedConnections?.connections ?? [];
  const effectiveSelectedExchangeId =
    selectedExchangeId ??
    savedConnections?.activeConnectionId ??
    exchangeConnections[0]?.id ??
    null;
  const selectedExchange =
    exchangeConnections.find((connection) => connection.id === effectiveSelectedExchangeId) ?? null;
  const assignedStrategyIds = useMemo(
    () =>
      assignments.find(
        (assignment) => assignment.exchangeConnectionId === effectiveSelectedExchangeId
      )?.strategyIds ?? [],
    [assignments, effectiveSelectedExchangeId]
  );
  const assignedStrategies = useMemo(
    () =>
      assignedStrategyIds.length > 0
        ? strategies.filter((strategy) => assignedStrategyIds.includes(strategy.id))
        : strategies.filter((strategy) => strategy.paperMode || strategy.status === 'RUNNING'),
    [assignedStrategyIds, strategies]
  );
  const effectiveSelectedAlgorithmId =
    selectedAlgorithmId && assignedStrategies.some((strategy) => strategy.id === selectedAlgorithmId)
      ? selectedAlgorithmId
      : assignedStrategies[0]?.id ?? null;
  const selectedAlgorithm =
    assignedStrategies.find((strategy) => strategy.id === effectiveSelectedAlgorithmId) ?? null;
  const { data: configHistory = [] } = useGetStrategyConfigHistoryQuery(
    selectedAlgorithm
      ? { strategyId: selectedAlgorithm.id, executionContext: 'paper' }
      : undefined,
    {
      skip: !selectedAlgorithm,
    }
  );
  const { data: selectedTradeHistory } = useGetTradeHistoryQuery(
    selectedAlgorithm
      ? {
          symbol: selectedAlgorithm.symbol,
          limit: 12,
          executionContext: 'paper',
        }
      : undefined,
    {
      skip: !selectedAlgorithm,
      pollingInterval: 15000,
      skipPollingIfUnfocused: true,
    }
  );
  const { data: auditEvents, isLoading: isAuditLoading } = useGetAuditEventsQuery({
    environment: 'paper',
    targetType: 'STRATEGY',
    limit: 8,
  });
  const selectedTrades = useMemo(() => selectedTradeHistory?.items ?? [], [selectedTradeHistory?.items]);
  const selectedOrders = useMemo(
    () =>
      selectedAlgorithm
        ? orders.filter((order) => order.symbol === selectedAlgorithm.symbol)
        : [],
    [orders, selectedAlgorithm]
  );
  const summaryItems = useMemo<PageMetricItem[]>(() => {
    if (!state) {
      return [];
    }

    return [
      {
        label: 'Desk Mode',
        value: state.paperMode ? 'Paper active' : 'Paper inactive',
        detail: state.incidentSummary,
        tone: state.paperMode ? 'success' : 'warning',
      },
      {
        label: 'Recovery',
        value: state.recoveryStatus,
        detail: state.recoveryMessage,
        tone:
          state.recoveryStatus === 'ATTENTION'
            ? 'warning'
            : state.recoveryStatus === 'HEALTHY'
              ? 'success'
              : 'info',
      },
      {
        label: 'Open Orders',
        value: String(state.openOrders),
        detail: `Filled ${state.filledOrders} | Cancelled ${state.cancelledOrders}`,
        tone: 'info',
      },
      {
        label: 'Stale State',
        value: `${state.staleOpenOrderCount} orders / ${state.stalePositionCount} positions`,
        detail: 'Use the summary panel below before taking any follow-up action.',
        tone:
          state.staleOpenOrderCount > 0 || state.stalePositionCount > 0
            ? 'warning'
            : 'success',
      },
      {
        label: 'Assigned Strategies',
        value: String(assignedStrategies.length),
        detail: selectedExchange
          ? `Paper assignments scoped to ${selectedExchange.name}.`
          : 'Select an exchange profile to scope paper assignments.',
        tone: assignedStrategies.length > 0 ? 'info' : 'warning',
      },
    ];
  }, [assignedStrategies.length, selectedExchange, state]);

  const statusItems = useMemo<ExecutionStatusItem[]>(() => {
    if (!selectedAlgorithm || !selectedExchange) {
      return [];
    }

    return [
      {
        label: 'Exchange profile',
        value: selectedExchange.name,
        detail: `${selectedExchange.exchange.toUpperCase()} ${selectedExchange.testnet ? 'TESTNET' : 'LIVE PROFILE'}`,
        tone: selectedExchange.testnet ? 'success' : 'warning',
      },
      {
        label: 'Assigned strategy',
        value: selectedAlgorithm.name,
        detail: `${selectedAlgorithm.symbol} | ${selectedAlgorithm.timeframe} | v${selectedAlgorithm.configVersion}`,
        tone: 'info',
      },
      {
        label: 'Paper status',
        value: selectedAlgorithm.status,
        detail: selectedAlgorithm.paperMode ? 'Simulation remains enabled.' : 'Monitoring only.',
        tone: selectedAlgorithm.status === 'RUNNING' ? 'success' : 'warning',
      },
      {
        label: 'Order activity',
        value: `${selectedOrders.length} paper orders`,
        detail: selectedOrders[0]?.createdAt
          ? `Last order ${selectedOrders[0].createdAt}`
          : 'No paper orders for this strategy symbol yet.',
        tone: selectedOrders.length > 0 ? 'info' : 'warning',
      },
    ];
  }, [selectedAlgorithm, selectedExchange, selectedOrders]);

  const metricItems = useMemo<LiveMetricItem[]>(() => {
    if (!selectedAlgorithm) {
      return [];
    }

    const openTradeCount = selectedTrades.filter((trade) => !trade.exitTime).length;

    return [
      {
        label: 'Strategy PnL',
        value: selectedAlgorithm.profitLoss.toFixed(2),
        detail: `${selectedAlgorithm.tradeCount} trades recorded`,
        tone: selectedAlgorithm.profitLoss >= 0 ? 'success' : 'warning',
        kicker: 'Paper',
      },
      {
        label: 'Drawdown',
        value: selectedAlgorithm.currentDrawdown.toFixed(2),
        detail: `${openTradeCount} open trade(s) in review`,
        tone: selectedAlgorithm.currentDrawdown > 0 ? 'warning' : 'success',
        kicker: 'Current',
      },
      {
        label: 'Position sizing',
        value: `${selectedAlgorithm.minPositionSize.toFixed(2)} - ${selectedAlgorithm.maxPositionSize.toFixed(2)}`,
        detail: `${(selectedAlgorithm.riskPerTrade * 100).toFixed(2)}% risk per trade`,
        tone: 'info',
        kicker: 'Config',
      },
      {
        label: 'Desk recovery',
        value: state?.recoveryStatus ?? 'Loading',
        detail: state ? 'Review incidents and audit entries below.' : 'Loading paper desk posture.',
        tone:
          state?.recoveryStatus === 'ATTENTION'
            ? 'warning'
            : state?.recoveryStatus === 'HEALTHY'
              ? 'success'
              : 'info',
        kicker: 'Desk',
      },
    ];
  }, [selectedAlgorithm, selectedTrades, state]);

  const detailSections = useMemo<ActiveAlgorithmDetailSection[]>(() => {
    if (!selectedAlgorithm) {
      return [];
    }

    const profile = getStrategyProfile(selectedAlgorithm.type);

    return [
      {
        id: 'parameters',
        title: 'Assigned parameters',
        content: (
          <Stack spacing={0.75}>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Strategy type:</strong> {profile?.title ?? selectedAlgorithm.type}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Entry rule:</strong> {profile?.entryRule ?? 'No profile metadata available.'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Exit rule:</strong> {profile?.exitRule ?? 'No profile metadata available.'}
            </Typography>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              <strong>Risk notes:</strong> {profile?.riskNotes ?? 'No profile metadata available.'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'config-history',
        title: 'Recent config versions',
        content: configHistory.length > 0 ? (
          <Stack spacing={1}>
            {configHistory.slice(0, 4).map((entry) => (
              <Stack key={entry.id} spacing={0.35} sx={{ minWidth: 0 }}>
                <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                  {`v${entry.versionNumber} | ${entry.symbol} | ${entry.timeframe}`}
                </Typography>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  {entry.changeReason}
                </Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No config history available yet.
          </Typography>
        ),
      },
      {
        id: 'paper-orders',
        title: 'Order events behind current state',
        content: selectedOrders.length > 0 ? (
          <Stack spacing={1}>
            {selectedOrders.slice(0, 5).map((order) => (
              <Stack key={order.id} spacing={0.35} sx={{ minWidth: 0 }}>
                <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                  {`#${order.id} | ${order.side} | ${order.status}`}
                </Typography>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  Qty {order.quantity} at {order.price} | Fill {order.fillPrice ?? 'Pending'}
                </Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No paper orders exist yet for this strategy symbol.
          </Typography>
        ),
      },
    ];
  }, [configHistory, selectedAlgorithm, selectedOrders]);

  const investigationEntries = useMemo<InvestigationLogEntry[]>(() => {
    const auditLogEntries =
      auditEvents?.events
        .filter(
          (event) =>
            !selectedAlgorithm || event.targetId === null || event.targetId === String(selectedAlgorithm.id)
        )
        .map((event) => ({
          id: `audit-${event.id}`,
          timestamp: event.createdAt,
          title: `${event.action} ${event.outcome.toLowerCase()}`,
          detail: event.details ?? `${event.actor} updated strategy state.`,
          tone: event.outcome === 'FAILED' ? 'warning' as const : 'success' as const,
          tags: [event.environment, event.actor, event.targetType],
        })) ?? [];

    const alertEntries =
      state?.alerts.map((alert, index) => ({
        id: `alert-${index}`,
        timestamp: 'Current desk state',
        title: `${alert.code}: ${alert.summary}`,
        detail: alert.recommendedAction,
        tone: alert.severity === 'WARNING' ? 'warning' as const : 'info' as const,
        tags: ['Paper desk', alert.severity],
      })) ?? [];

    return [...alertEntries, ...auditLogEntries];
  }, [auditEvents?.events, selectedAlgorithm, state?.alerts]);

  const toggleAssignment = (strategy: Strategy) => {
    if (!effectiveSelectedExchangeId) {
      setFeedback({ severity: 'error', message: 'Select an exchange profile before assigning strategies.' });
      return;
    }

    const wasAssigned = assignedStrategyIds.includes(strategy.id);
    const nextAssignments = togglePaperWorkspaceAssignment(
      assignments,
      effectiveSelectedExchangeId,
      strategy.id
    );
    setAssignments(nextAssignments);
    setSelectedAlgorithmId(strategy.id);
    setFeedback({
      severity: 'success',
      message: wasAssigned
        ? `${strategy.name} removed from ${selectedExchange?.name ?? 'the selected exchange profile'}.`
        : `${strategy.name} assigned to ${selectedExchange?.name ?? 'the selected exchange profile'}.`,
    });
  };

  const onSubmitOrder = async () => {
    const quantity = Number(form.quantity);
    const price = Number(form.price);
    if (!form.symbol.trim()) {
      setFeedback({ severity: 'error', message: 'Symbol is required.' });
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setFeedback({ severity: 'error', message: 'Quantity must be greater than zero.' });
      return;
    }
    if (!Number.isFinite(price) || price <= 0) {
      setFeedback({ severity: 'error', message: 'Price must be greater than zero.' });
      return;
    }

    try {
      const response = await placeOrder({
        symbol: form.symbol.trim(),
        side: form.side,
        quantity,
        price,
        executeNow: form.executeNow,
      }).unwrap();
      setFeedback({
        severity: 'success',
        message: `Paper order #${response.id} submitted with status ${response.status}.`,
      });
      setForm((current) => ({
        ...current,
        symbol: form.symbol.trim(),
      }));
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const onFillOrder = async (orderId: number) => {
    try {
      const response = await fillOrder(orderId).unwrap();
      setFeedback({
        severity: 'success',
        message: `Paper order #${response.id} filled at ${
          response.fillPrice ?? response.price
        }.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const onCancelOrder = async (orderId: number) => {
    try {
      const response = await cancelOrder(orderId).unwrap();
      setFeedback({
        severity: 'success',
        message: `Paper order #${response.id} cancelled.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const onToggleStrategyRuntime = async (strategy: Strategy) => {
    try {
      if (strategy.status === 'RUNNING') {
        await stopStrategy(strategy.id).unwrap();
        setFeedback({ severity: 'success', message: `${strategy.name} stopped in paper mode.` });
      } else {
        await startStrategy(strategy.id).unwrap();
        setFeedback({ severity: 'success', message: `${strategy.name} started in paper mode.` });
      }
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const onSaveConfig = async (strategy: Strategy, payload: StrategyConfigOutput) => {
    await updateConfig({
      strategyId: strategy.id,
      ...payload,
    }).unwrap();
    setFeedback({ severity: 'success', message: `${strategy.name} configuration updated.` });
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Paper-only execution"
          description="Place, fill, and cancel simulated orders from one desk without hiding recovery state, stale positions, or audit-sensitive context."
          chips={
            <>
              <Chip label="Orders stay simulated" variant="outlined" />
              <Chip label="Assignments stay exchange-scoped" variant="outlined" />
              <Chip label="Fees and slippage remain visible" variant="outlined" />
              <Chip label="Live routing is still blocked" variant="outlined" />
            </>
          }
        />

        {summaryItems.length > 0 ? <PageMetricStrip items={summaryItems} /> : null}

        <ExecutionStatusRail
          title="Paper workspace posture"
          description="Exchange selection, assigned strategy state, and desk health stay visible before you touch order entry."
          items={statusItems}
        />

        <LiveMetricStrip
          title="Active paper algorithm"
          description="Review performance, drawdown, and desk recovery for the currently selected exchange-scoped paper strategy."
          items={metricItems}
        />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, xl: 7 }}>
            <SurfacePanel
              title="Exchange-scoped strategy assignment"
              description="Assignments are stored on this workstation until the backend exposes a durable paper-assignment API."
              actions={
                selectedAlgorithm ? (
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                    <Button
                      size="small"
                      variant={
                        assignedStrategyIds.includes(selectedAlgorithm.id) ? 'outlined' : 'contained'
                      }
                      onClick={() => toggleAssignment(selectedAlgorithm)}
                    >
                      {assignedStrategyIds.includes(selectedAlgorithm.id)
                        ? 'Unassign strategy'
                        : 'Assign to exchange'}
                    </Button>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => setEditingStrategy(selectedAlgorithm)}
                    >
                      Edit params
                    </Button>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => void onToggleStrategyRuntime(selectedAlgorithm)}
                    >
                      {selectedAlgorithm.status === 'RUNNING' ? 'Stop' : 'Start'}
                    </Button>
                  </Stack>
                ) : null
              }
            >
              {exchangeConnections.length === 0 ? (
                <EmptyState
                  title="No exchange profiles available"
                  description="Create or activate an exchange connection profile before assigning strategies to a paper desk so the main workspace is no longer blocked by an empty selection column."
                  tone="info"
                  action={
                    <Button component={RouterLink} to="/settings" variant="contained" size="small">
                      Open Settings
                    </Button>
                  }
                  secondaryAction={
                    <Button component={RouterLink} to="/strategies" variant="outlined" size="small">
                      Review Strategies
                    </Button>
                  }
                />
              ) : (
                <Stack spacing={1.5}>
                  <FormControl fullWidth>
                    <InputLabel id="paper-exchange-profile-label">Exchange profile</InputLabel>
                    <Select
                      labelId="paper-exchange-profile-label"
                      value={effectiveSelectedExchangeId ?? ''}
                      label="Exchange profile"
                      onChange={(event) => setSelectedExchangeId(String(event.target.value))}
                    >
                      {exchangeConnections.map((connection) => (
                        <MenuItem key={connection.id} value={connection.id}>
                          {`${connection.name} | ${connection.exchange.toUpperCase()} ${connection.testnet ? 'TESTNET' : 'LIVE PROFILE'}`}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>

                  {strategies.length === 0 ? (
                    <EmptyState
                      title="No strategies available"
                      description="Saved paper strategies will appear here once the strategy catalog is available."
                      tone="info"
                    />
                  ) : (
                    <Stack spacing={1.25}>
                      {strategies.map((strategy) => {
                        const assigned = assignedStrategyIds.includes(strategy.id);

                        return (
                          <ExecutionCard
                            key={strategy.id}
                            title={strategy.name}
                            subtitle={`${strategy.symbol} | ${strategy.timeframe} | ${strategy.type}`}
                            selected={strategy.id === effectiveSelectedAlgorithmId}
                            onSelect={() => setSelectedAlgorithmId(strategy.id)}
                            ariaLabel={`Select ${strategy.name} in paper workspace`}
                            badges={
                              <>
                                <StatusPill
                                  label={assigned ? 'Assigned to exchange' : 'Not assigned'}
                                  tone={assigned ? 'success' : 'warning'}
                                />
                                <StatusPill
                                  label={strategy.status}
                                  tone={strategy.status === 'RUNNING' ? 'success' : 'default'}
                                />
                              </>
                            }
                            metrics={[
                              {
                                label: 'PnL',
                                value: strategy.profitLoss.toFixed(2),
                                tone: strategy.profitLoss >= 0 ? 'success' : 'warning',
                              },
                              { label: 'Trades', value: String(strategy.tradeCount), tone: 'info' },
                              {
                                label: 'Drawdown',
                                value: strategy.currentDrawdown.toFixed(2),
                                tone: strategy.currentDrawdown > 0 ? 'warning' : 'success',
                              },
                              {
                                label: 'Config',
                                value: `v${strategy.configVersion}`,
                                tone: 'info',
                              },
                            ]}
                            detail={getStrategyProfile(strategy.type)?.bestFor ?? 'Open the strategy detail drawer for rule guidance and current paper-state context.'}
                          />
                        );
                      })}
                    </Stack>
                  )}
                </Stack>
              )}
            </SurfacePanel>

            <ForwardSignalTimelineChart
              strategyName={selectedAlgorithm?.name ?? 'Selected paper strategy'}
              trades={selectedTrades}
            />
          </Grid>

          <Grid size={{ xs: 12, xl: 5 }}>
            <ActiveAlgorithmExplainabilityPanel
              title={selectedAlgorithm ? `${selectedAlgorithm.name} paper detail` : 'Paper algorithm detail'}
              description="Selecting an assigned paper algorithm keeps signal evidence, parameters, and order events together."
              desktopBehavior="inline"
              desktopBreakpoint="lg"
              subject={
                selectedAlgorithm
                  ? {
                      name: selectedAlgorithm.name,
                      status: selectedAlgorithm.status,
                      symbol: selectedAlgorithm.symbol,
                      timeframe: selectedAlgorithm.timeframe,
                      riskPerTrade: selectedAlgorithm.riskPerTrade,
                      minPositionSize: selectedAlgorithm.minPositionSize,
                      maxPositionSize: selectedAlgorithm.maxPositionSize,
                      configVersion: selectedAlgorithm.configVersion,
                      lastConfigChangedAt: selectedAlgorithm.lastConfigChangedAt,
                    }
                  : null
              }
              profile={selectedAlgorithm ? getStrategyProfile(selectedAlgorithm.type) : null}
              trades={selectedTrades}
              incidents={investigationEntries}
              loading={isOrdersLoading}
              summary={
                selectedAlgorithm ? (
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      {selectedExchange
                        ? `${selectedAlgorithm.name} is scoped to ${selectedExchange.name}.`
                        : 'Select an exchange profile to review paper assignment context.'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Open trade count:</strong> {selectedTrades.filter((trade) => !trade.exitTime).length}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Recent order events:</strong> {selectedOrders.length}
                    </Typography>
                  </Stack>
                ) : null
              }
              extraSections={detailSections}
            />

            <InvestigationLogPanel
              title="Paper incidents and audit trail"
              description="Desk alerts and strategy audit entries stay visible while you manage exchange-scoped paper assignments."
              entries={investigationEntries}
              loading={isAuditLoading}
            />
          </Grid>

          <Grid size={{ xs: 12, lg: 7 }}>
            <PaperOrderEntryPanel
              form={form}
              busy={orderMutationBusy}
              onChange={(next) =>
                setForm({
                  ...next,
                  symbol: sanitizeText(next.symbol),
                })
              }
              onSubmit={onSubmitOrder}
            />
          </Grid>

          <Grid size={{ xs: 12, lg: 5 }}>
            {isStateLoading ? <Alert severity="info">Loading paper-trading state...</Alert> : null}
            {isStateError ? (
              <Alert severity="error">Unable to load paper-trading state.</Alert>
            ) : null}
            {state ? <PaperTradingSummaryPanel state={state} /> : null}
          </Grid>

          <Grid size={{ xs: 12 }}>
            {selectedAlgorithm ? (
              <SurfacePanel
                title="Paper order events for selected strategy"
                description="These orders contribute directly to the current paper-state review for the selected algorithm."
              >
                {selectedOrders.length === 0 ? (
                  <EmptyState
                    title="No paper orders for this strategy"
                    description="Order events appear here once the assigned strategy symbol has paper-order activity."
                    tone="info"
                  />
                ) : (
                  <Stack spacing={1}>
                    {selectedOrders.map((order) => (
                      <Stack
                        key={order.id}
                        direction={{ xs: 'column', md: 'row' }}
                        spacing={1}
                        justifyContent="space-between"
                        sx={{ borderTop: '1px solid', borderColor: 'divider', pt: 1 }}
                      >
                        <Stack spacing={0.35}>
                          <Typography variant="subtitle2">{`#${order.id} | ${order.side} | ${order.status}`}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {order.symbol} | Created {order.createdAt}
                          </Typography>
                        </Stack>
                        <Stack spacing={0.35} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                          <NumericText variant="body2">{`${order.quantity} @ ${order.price}`}</NumericText>
                          <Typography variant="caption" color="text.secondary">
                            Fill {order.fillPrice ?? 'Pending'} | Fees {order.fees ?? 'Pending'}
                          </Typography>
                        </Stack>
                      </Stack>
                    ))}
                  </Stack>
                )}
              </SurfacePanel>
            ) : null}

            {isOrdersLoading ? <Alert severity="info">Loading paper orders...</Alert> : null}
            {isOrdersError ? (
              <Alert severity="error">Unable to load paper orders.</Alert>
            ) : null}
            {!isOrdersLoading && !isOrdersError ? (
              <PaperOrdersTablePanel
                orders={orders}
                busy={orderMutationBusy}
                onFill={onFillOrder}
                onCancel={onCancelOrder}
              />
            ) : null}
          </Grid>
        </Grid>
      </PageContent>

      {editingStrategy ? (
        <StrategyConfigModal
          key={editingStrategy.id}
          strategy={editingStrategy}
          busy={isSavingConfig}
          onClose={() => setEditingStrategy(null)}
          onSave={onSaveConfig}
        />
      ) : null}
    </AppLayout>
  );
}
